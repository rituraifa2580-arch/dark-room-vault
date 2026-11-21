package com.example.security

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

enum class AutoLockDelay(val label: String, val seconds: Long) {
    IMMEDIATE("Immediately on background", 0L),
    FIFTEEN_SECONDS("15 seconds", 15L),
    ONE_MINUTE("1 minute", 60L),
    FIVE_MINUTES("5 minutes", 300L)
}

@Serializable
data class VaultConfigSnapshot(
    val masterPinHashB64: String? = null,
    val masterPinSaltB64: String? = null,
    val decoyPinHashB64: String? = null,
    val decoyPinSaltB64: String? = null,
    val biometricEnabled: Boolean = false,
    val autoLockDelayName: String = AutoLockDelay.IMMEDIATE.name,
    val screenshotShieldEnabled: Boolean = true,
    val stealthCoverEnabled: Boolean = false,
    val threeFingerGestureEnabled: Boolean = true,
    val intruderSelfieEnabled: Boolean = false,
    val appLockEnabled: Boolean = false,
    val lockedPackages: Set<String> = emptySet(),
    val failedAttemptCount: Int = 0,
    val lockoutUntilEpochMs: Long = 0L
) {
    val isSetupComplete: Boolean
        get() = !masterPinHashB64.isNullOrBlank() && !masterPinSaltB64.isNullOrBlank()

    val hasDecoyPin: Boolean
        get() = !decoyPinHashB64.isNullOrBlank() && !decoyPinSaltB64.isNullOrBlank()

    val autoLockDelay: AutoLockDelay
        get() = AutoLockDelay.entries.firstOrNull { it.name == autoLockDelayName }
            ?: AutoLockDelay.IMMEDIATE
}

enum class PinVerifyResult {
    MASTER,
    DECOY,
    INVALID
}

/**
 * Stores all sensitive vault settings and salted PBKDF2 PIN hashes inside an
 * Android Keystore AES-256-GCM encrypted file (`vault_config.enc`).
 */
class SecureVaultPrefs(
    context: Context,
    private val cryptoEngine: VaultCryptoEngine
) {
    private val configFile = File(context.filesDir, "vault_config.enc")
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val aad = "darkroom_vault_config_v1".toByteArray(Charsets.UTF_8)

    private val _configFlow = MutableStateFlow(loadInitialSync())
    val configFlow: StateFlow<VaultConfigSnapshot> = _configFlow.asStateFlow()

    val currentConfig: VaultConfigSnapshot
        get() = _configFlow.value

    private fun loadInitialSync(): VaultConfigSnapshot {
        if (!configFile.exists()) return VaultConfigSnapshot()
        return try {
            val encrypted = configFile.readBytes()
            val plain = cryptoEngine.decryptBytes(encrypted, aad)
            json.decodeFromString<VaultConfigSnapshot>(plain.toString(Charsets.UTF_8))
        } catch (_: Exception) {
            VaultConfigSnapshot()
        }
    }

    suspend fun updateConfig(transform: (VaultConfigSnapshot) -> VaultConfigSnapshot): VaultConfigSnapshot {
        return mutex.withLock {
            val updated = transform(_configFlow.value)
            val jsonBytes = json.encodeToString(updated).toByteArray(Charsets.UTF_8)
            val encrypted = cryptoEngine.encryptBytes(jsonBytes, aad)
            val tmp = File(configFile.parentFile, "${configFile.name}.tmp")
            tmp.writeBytes(encrypted)
            if (configFile.exists()) configFile.delete()
            if (!tmp.renameTo(configFile)) {
                tmp.copyTo(configFile, overwrite = true)
                tmp.delete()
            }
            jsonBytes.fill(0)
            _configFlow.value = updated
            updated
        }
    }

    suspend fun setMasterPin(pin: String) {
        require(pin.length in 4..6 && pin.all { it.isDigit() }) { "PIN must be 4 to 6 digits" }
        val salt = cryptoEngine.generateSalt()
        val hash = cryptoEngine.hashPin(pin, salt)
        updateConfig {
            it.copy(
                masterPinHashB64 = Base64.encodeToString(hash, Base64.NO_WRAP),
                masterPinSaltB64 = Base64.encodeToString(salt, Base64.NO_WRAP),
                failedAttemptCount = 0,
                lockoutUntilEpochMs = 0L
            )
        }
        hash.fill(0)
    }

    suspend fun setDecoyPin(pin: String) {
        require(pin.length in 4..6 && pin.all { it.isDigit() }) { "Decoy PIN must be 4 to 6 digits" }
        val salt = cryptoEngine.generateSalt()
        val hash = cryptoEngine.hashPin(pin, salt)
        updateConfig {
            it.copy(
                decoyPinHashB64 = Base64.encodeToString(hash, Base64.NO_WRAP),
                decoyPinSaltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
            )
        }
        hash.fill(0)
    }

    suspend fun clearDecoyPin() {
        updateConfig {
            it.copy(decoyPinHashB64 = null, decoyPinSaltB64 = null)
        }
    }

    fun verifyPin(pin: String, allowDecoy: Boolean = true): PinVerifyResult {
        val snap = _configFlow.value
        val masterHashB64 = snap.masterPinHashB64 ?: return PinVerifyResult.INVALID
        val masterSaltB64 = snap.masterPinSaltB64 ?: return PinVerifyResult.INVALID
        val masterMatches = cryptoEngine.verifyPin(
            pin = pin,
            salt = Base64.decode(masterSaltB64, Base64.NO_WRAP),
            expectedHash = Base64.decode(masterHashB64, Base64.NO_WRAP)
        )
        if (masterMatches) return PinVerifyResult.MASTER

        if (allowDecoy && snap.hasDecoyPin) {
            val decoyHash = Base64.decode(snap.decoyPinHashB64!!, Base64.NO_WRAP)
            val decoySalt = Base64.decode(snap.decoyPinSaltB64!!, Base64.NO_WRAP)
            if (cryptoEngine.verifyPin(pin, decoySalt, decoyHash)) {
                return PinVerifyResult.DECOY
            }
        }
        return PinVerifyResult.INVALID
    }

    fun matchesMasterPin(pin: String): Boolean = verifyPin(pin, allowDecoy = false) == PinVerifyResult.MASTER

    fun matchesDecoyPin(pin: String): Boolean {
        val snap = _configFlow.value
        if (!snap.hasDecoyPin) return false
        val decoyHash = Base64.decode(snap.decoyPinHashB64!!, Base64.NO_WRAP)
        val decoySalt = Base64.decode(snap.decoyPinSaltB64!!, Base64.NO_WRAP)
        return cryptoEngine.verifyPin(pin, decoySalt, decoyHash)
    }
}
