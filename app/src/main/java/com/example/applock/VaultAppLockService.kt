package com.example.applock

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.example.DarkRoomApplication

object AccessibilityServiceHelper {
    fun isAppLockServiceEnabled(context: Context): Boolean {
        val expectedComponent = ComponentName(context, VaultAppLockService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            val componentNameString = splitter.next()
            val enabledService = ComponentName.unflattenFromString(componentNameString)
            if (enabledService == expectedComponent) {
                return true
            }
        }
        return false
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

/**
 * Minimal-privilege AccessibilityService listening strictly to TYPE_WINDOW_STATE_CHANGED
 * without reading window content (`android:canRetrieveWindowContent="false"`).
 */
class VaultAppLockService : AccessibilityService() {

    private var lastPromptPackage: String? = null
    private var lastPromptElapsedMs: Long = 0L

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                AppLockSessionCache.clearAll()
                lastPromptPackage = null
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (_: Exception) {
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return

        // Ignore our own package, SystemUI, Android Settings, and active IME keyboards
        if (pkg == packageName ||
            pkg == "com.android.systemui" ||
            pkg == "com.android.settings" ||
            isInputMethodPackage(pkg)
        ) {
            return
        }

        val container = (application as? DarkRoomApplication)?.container ?: return
        val config = container.prefs.currentConfig
        if (!config.isSetupComplete || !config.appLockEnabled) return
        if (!config.lockedPackages.contains(pkg)) return
        if (AppLockSessionCache.isTemporarilyUnlocked(pkg)) return

        val now = SystemClock.elapsedRealtime()
        if (pkg == lastPromptPackage && now - lastPromptElapsedMs < 1500L) {
            return
        }
        lastPromptPackage = pkg
        lastPromptElapsedMs = now

        val appLabel = try {
            val appInfo = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            pkg
        }

        val challengeIntent = Intent(this, AppLockChallengeActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(AppLockChallengeActivity.EXTRA_TARGET_PACKAGE, pkg)
            putExtra(AppLockChallengeActivity.EXTRA_TARGET_APP_NAME, appLabel)
        }
        startActivity(challengeIntent)
    }

    override fun onInterrupt() {
        // No-op
    }

    private fun isInputMethodPackage(pkg: String): Boolean {
        return try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.enabledInputMethodList?.any { it.packageName == pkg } == true
        } catch (_: Exception) {
            false
        }
    }
}
