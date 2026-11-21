package com.example.applock

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.DarkRoomApplication
import com.example.security.BiometricAuthHelper
import com.example.security.BiometricAvailability
import com.example.security.IntruderCameraCapture
import com.example.ui.components.LuxuryPinSection
import com.example.ui.components.ObsidianVaultBackground
import com.example.ui.components.SecurityBadgeChip
import com.example.ui.theme.CoolSlateText
import com.example.ui.theme.DarkRoomTheme
import com.example.ui.theme.ImperialGold
import com.example.ui.theme.IvoryText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen FLAG_SECURE challenge overlay shown when a protected third-party app is launched.
 */
class AppLockChallengeActivity : FragmentActivity() {

    companion object {
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
        const val EXTRA_TARGET_APP_NAME = "extra_target_app_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE).orEmpty()
        val targetAppName = intent.getStringExtra(EXTRA_TARGET_APP_NAME).orEmpty()
            .ifBlank { targetPackage }

        val container = (application as DarkRoomApplication).container
        val prefs = container.prefs
        val repository = container.repository
        val cryptoEngine = container.cryptoEngine

        val biometricReady = prefs.currentConfig.biometricEnabled &&
            BiometricAuthHelper.checkAvailability(this) == BiometricAvailability.AVAILABLE

        fun unlockTargetApp() {
            if (targetPackage.isNotBlank()) {
                AppLockSessionCache.grantTemporaryAccess(targetPackage)
            }
            finish()
        }

        fun launchBiometric() {
            BiometricAuthHelper.authenticate(
                activity = this,
                title = "Unlock $targetAppName",
                subtitle = "Protected by Dark Room App Lock",
                negativeButtonText = "Use Master PIN",
                onSuccess = { unlockTargetApp() },
                onError = { _, _ -> },
                onFailed = { }
            )
        }

        if (biometricReady) {
            launchBiometric()
        }

        setContent {
            DarkRoomTheme {
                var pin by remember { mutableStateOf("") }
                var errorText by remember { mutableStateOf<String?>(null) }
                var shakeTrigger by remember { mutableIntStateOf(0) }
                var failedCount by remember { mutableIntStateOf(0) }
                var lockoutSeconds by remember { mutableIntStateOf(0) }

                BackHandler {
                    // Navigate safely to device launcher home so locked app cannot be bypassed
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(homeIntent)
                    finish()
                }

                androidx.compose.runtime.LaunchedEffect(lockoutSeconds) {
                    if (lockoutSeconds > 0) {
                        delay(1000L)
                        lockoutSeconds -= 1
                    }
                }

                ObsidianVaultBackground {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .systemBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = "Protected application",
                            tint = ImperialGold,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SecurityBadgeChip(label = "DARK ROOM APP LOCK")
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = targetAppName,
                            style = MaterialTheme.typography.headlineMedium,
                            color = IvoryText,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Authenticate with your Master PIN or fingerprint to open this app.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CoolSlateText,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        LuxuryPinSection(
                            pin = pin,
                            errorText = errorText,
                            lockoutSecondsRemaining = lockoutSeconds,
                            showBiometricButton = biometricReady,
                            confirmButtonLabel = "Unlock App",
                            shakeTrigger = shakeTrigger,
                            onDigitPress = { digit ->
                                if (pin.length < 6) {
                                    pin += digit
                                    errorText = null
                                }
                            },
                            onBackspace = {
                                if (pin.isNotEmpty()) {
                                    pin = pin.dropLast(1)
                                    errorText = null
                                }
                            },
                            onSubmit = {
                                if (prefs.matchesMasterPin(pin)) {
                                    unlockTargetApp()
                                } else {
                                    val attemptedLen = pin.length
                                    pin = ""
                                    failedCount += 1
                                    shakeTrigger += 1
                                    errorText = "Incorrect Master PIN"
                                    if (failedCount >= 5) {
                                        lockoutSeconds = 30
                                    }
                                    lifecycleScope.launch {
                                        val photoPath = if (prefs.currentConfig.intruderSelfieEnabled) {
                                            IntruderCameraCapture.captureEncryptedSelfie(
                                                context = this@AppLockChallengeActivity,
                                                lifecycleOwner = this@AppLockChallengeActivity,
                                                cryptoEngine = cryptoEngine
                                            )
                                        } else {
                                            null
                                        }
                                        repository.recordIntruderAttempt(
                                            source = "App Lock: $targetAppName",
                                            attemptCount = failedCount,
                                            enteredLength = attemptedLen,
                                            outcome = "Invalid Master PIN",
                                            encryptedPhotoPath = photoPath
                                        )
                                    }
                                }
                            },
                            onBiometricClick = { launchBiometric() }
                        )
                    }
                }
            }
        }
    }
}
