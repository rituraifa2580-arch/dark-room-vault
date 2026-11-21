package com.example.applock

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks temporarily unlocked third-party apps for the active screen-on session.
 * Cleared immediately when the device screen turns off or when the user manually locks.
 */
object AppLockSessionCache {
    private val unlockedUntilElapsedMs = ConcurrentHashMap<String, Long>()
    private const val SESSION_GRACE_MS = 120_000L // 2 minutes per unlock or until screen off

    fun grantTemporaryAccess(packageName: String) {
        unlockedUntilElapsedMs[packageName] = SystemClock.elapsedRealtime() + SESSION_GRACE_MS
    }

    fun isTemporarilyUnlocked(packageName: String): Boolean {
        val expiry = unlockedUntilElapsedMs[packageName] ?: return false
        return if (SystemClock.elapsedRealtime() < expiry) {
            true
        } else {
            unlockedUntilElapsedMs.remove(packageName)
            false
        }
    }

    fun clearAll() {
        unlockedUntilElapsedMs.clear()
    }
}
