package com.example.ui.state

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.DarkRoomApplication
import com.example.applock.AccessibilityServiceHelper
import com.example.applock.AppLockSessionCache
import com.example.data.InstalledAppInfo
import com.example.data.IntruderLogEntity
import com.example.data.MediaType
import com.example.data.VaultMediaEntity
import com.example.data.VaultScope
import com.example.security.AutoLockDelay
import com.example.security.BiometricAuthHelper
import com.example.security.BiometricAvailability
import com.example.security.IntruderCameraCapture
import com.example.security.PinVerifyResult
import com.example.security.VaultConfigSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface VaultAuthState {
    data object NeedsOnboarding : VaultAuthState
    data class Locked(
        val stealthCoverActive: Boolean,
        val secretOverlayRevealed: Boolean = false
    ) : VaultAuthState

    data class Unlocked(val scope: VaultScope) : VaultAuthState
}

sealed interface ImportStatus {
    data object Idle : ImportStatus
    data class Encrypting(val current: Int, val total: Int, val label: String) : ImportStatus
}

sealed interface VaultEvent {
    data class ShowMessage(val message: String, val isError: Boolean = false) : VaultEvent
    data object RequestBiometricOnLock : VaultEvent
    data object NavigateToVaultHome : VaultEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as DarkRoomApplication).container
    val cryptoEngine = container.cryptoEngine
    private val prefs = container.prefs
    val repository = container.repository

    val config: StateFlow<VaultConfigSnapshot> = prefs.configFlow
    val hardwareKeystoreActive: Boolean = cryptoEngine.isKeystoreHardwareBacked()

    private val _authState = MutableStateFlow(
        if (prefs.currentConfig.isSetupComplete) {
            VaultAuthState.Locked(
                stealthCoverActive = prefs.currentConfig.stealthCoverEnabled,
                secretOverlayRevealed = false
            )
        } else {
            VaultAuthState.NeedsOnboarding
        }
    )
    val authState: StateFlow<VaultAuthState> = _authState.asStateFlow()

    private val _importStatus = MutableStateFlow<ImportStatus>(ImportStatus.Idle)
    val importStatus: StateFlow<ImportStatus> = _importStatus.asStateFlow()

    private val _biometricAvailability = MutableStateFlow(
        BiometricAuthHelper.checkAvailability(application)
    )
    val biometricAvailability: StateFlow<BiometricAvailability> = _biometricAvailability.asStateFlow()

    private val _accessibilityServiceActive = MutableStateFlow(
        AccessibilityServiceHelper.isAppLockServiceEnabled(application)
    )
    val accessibilityServiceActive: StateFlow<Boolean> = _accessibilityServiceActive.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _secretVaultSwitchSheetVisible = MutableStateFlow(false)
    val secretVaultSwitchSheetVisible: StateFlow<Boolean> = _secretVaultSwitchSheetVisible.asStateFlow()

    private val _events = MutableSharedFlow<VaultEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private val _lockoutSecondsRemaining = MutableStateFlow(0)
    val lockoutSecondsRemaining: StateFlow<Int> = _lockoutSecondsRemaining.asStateFlow()

    private val _failedPinShakeTrigger = MutableStateFlow(0)
    val failedPinShakeTrigger: StateFlow<Int> = _failedPinShakeTrigger.asStateFlow()

    private val _pinErrorText = MutableStateFlow<String?>(null)
    val pinErrorText: StateFlow<String?> = _pinErrorText.asStateFlow()

    private val activeVaultScopeFlow = _authState.map { state ->
        (state as? VaultAuthState.Unlocked)?.scope
    }

    val allMedia: StateFlow<List<VaultMediaEntity>> = activeVaultScopeFlow
        .flatMapLatest { scope ->
            if (scope == null) flowOf(emptyList()) else repository.observeAllMedia(scope)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val photos: StateFlow<List<VaultMediaEntity>> = activeVaultScopeFlow
        .flatMapLatest { scope ->
            if (scope == null) flowOf(emptyList()) else repository.observeMediaByType(scope, MediaType.PHOTO)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val videos: StateFlow<List<VaultMediaEntity>> = activeVaultScopeFlow
        .flatMapLatest { scope ->
            if (scope == null) flowOf(emptyList()) else repository.observeMediaByType(scope, MediaType.VIDEO)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val audioTracks: StateFlow<List<VaultMediaEntity>> = activeVaultScopeFlow
        .flatMapLatest { scope ->
            if (scope == null) flowOf(emptyList()) else repository.observeMediaByType(scope, MediaType.AUDIO)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val intruderLogs: StateFlow<List<IntruderLogEntity>> = repository.observeIntruderLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var backgroundedAtElapsedMs: Long? = null
    private var lastInteractionElapsedMs: Long = SystemClock.elapsedRealtime()
    private var lockoutTickerJob: Job? = null

    init {
        refreshLockoutRemaining()
        startInactivityWatcher()
    }

    private fun startInactivityWatcher() {
        viewModelScope.launch {
            while (true) {
                delay(5000L)
                val currentAuth = _authState.value
                if (currentAuth is VaultAuthState.Unlocked) {
                    val delaySeconds = config.value.autoLockDelay.seconds.coerceAtLeast(120L)
                    val idleMs = SystemClock.elapsedRealtime() - lastInteractionElapsedMs
                    if (idleMs >= delaySeconds * 1000L) {
                        lockVault(reason = "Locked due to inactivity")
                    }
                }
            }
        }
    }

    fun recordUserInteraction() {
        lastInteractionElapsedMs = SystemClock.elapsedRealtime()
    }

    fun onAppBackgrounded() {
        if (_authState.value !is VaultAuthState.Unlocked) return
        val delaySec = config.value.autoLockDelay.seconds
        if (delaySec == 0L) {
            lockVault()
        } else {
            backgroundedAtElapsedMs = SystemClock.elapsedRealtime()
        }
    }

    fun onAppForegrounded() {
        refreshSystemSecurityStatus()
        val bgAt = backgroundedAtElapsedMs
        backgroundedAtElapsedMs = null
        if (bgAt != null && _authState.value is VaultAuthState.Unlocked) {
            val elapsedSec = (SystemClock.elapsedRealtime() - bgAt) / 1000L
            if (elapsedSec >= config.value.autoLockDelay.seconds) {
                lockVault()
            }
        }
    }

    fun refreshSystemSecurityStatus() {
        val app = getApplication<Application>()
        _biometricAvailability.value = BiometricAuthHelper.checkAvailability(app)
        _accessibilityServiceActive.value = AccessibilityServiceHelper.isAppLockServiceEnabled(app)
        refreshLockoutRemaining()
    }

    fun lockVault(reason: String? = null) {
        repository.clearInMemoryCaches()
        _secretVaultSwitchSheetVisible.value = false
        _pinErrorText.value = null
        if (prefs.currentConfig.isSetupComplete) {
            _authState.value = VaultAuthState.Locked(
                stealthCoverActive = prefs.currentConfig.stealthCoverEnabled,
                secretOverlayRevealed = false
            )
            if (reason != null) {
                _events.tryEmit(VaultEvent.ShowMessage(reason))
            }
        }
    }

    fun completeOnboarding(
        masterPin: String,
        decoyPin: String?,
        enableBiometric: Boolean,
        enableStealthCover: Boolean
    ) {
        viewModelScope.launch {
            prefs.setMasterPin(masterPin)
            if (!decoyPin.isNullOrBlank()) {
                prefs.setDecoyPin(decoyPin)
            }
            prefs.updateConfig {
                it.copy(
                    biometricEnabled = enableBiometric,
                    stealthCoverEnabled = enableStealthCover
                )
            }
            lastInteractionElapsedMs = SystemClock.elapsedRealtime()
            _authState.value = VaultAuthState.Unlocked(VaultScope.MASTER)
            _events.emit(VaultEvent.ShowMessage("Dark Room vault initialized with AES-256-GCM"))
        }
    }

    fun submitLockPin(
        pin: String,
        lifecycleOwner: LifecycleOwner?,
        onCleared: () -> Unit
    ) {
        if (_lockoutSecondsRemaining.value > 0) {
            onCleared()
            return
        }
        when (prefs.verifyPin(pin, allowDecoy = true)) {
            PinVerifyResult.MASTER -> {
                onCleared()
                _pinErrorText.value = null
                viewModelScope.launch {
                    prefs.updateConfig { it.copy(failedAttemptCount = 0, lockoutUntilEpochMs = 0L) }
                }
                lastInteractionElapsedMs = SystemClock.elapsedRealtime()
                _authState.value = VaultAuthState.Unlocked(VaultScope.MASTER)
            }

            PinVerifyResult.DECOY -> {
                onCleared()
                _pinErrorText.value = null
                viewModelScope.launch {
                    prefs.updateConfig { it.copy(failedAttemptCount = 0, lockoutUntilEpochMs = 0L) }
                }
                lastInteractionElapsedMs = SystemClock.elapsedRealtime()
                _authState.value = VaultAuthState.Unlocked(VaultScope.DECOY)
            }

            PinVerifyResult.INVALID -> {
                val enteredLen = pin.length
                onCleared()
                _failedPinShakeTrigger.value += 1
                _pinErrorText.value = "Invalid vault PIN"
                viewModelScope.launch {
                    val updated = prefs.updateConfig { snap ->
                        val nextCount = snap.failedAttemptCount + 1
                        val lockoutUntil = if (nextCount % 5 == 0) {
                            System.currentTimeMillis() + 30_000L
                        } else {
                            snap.lockoutUntilEpochMs
                        }
                        snap.copy(
                            failedAttemptCount = nextCount,
                            lockoutUntilEpochMs = lockoutUntil
                        )
                    }
                    refreshLockoutRemaining()

                    val photoPath = if (updated.intruderSelfieEnabled && lifecycleOwner != null) {
                        IntruderCameraCapture.captureEncryptedSelfie(
                            context = getApplication(),
                            lifecycleOwner = lifecycleOwner,
                            cryptoEngine = cryptoEngine
                        )
                    } else {
                        null
                    }
                    repository.recordIntruderAttempt(
                        source = "Vault Lock",
                        attemptCount = updated.failedAttemptCount,
                        enteredLength = enteredLen,
                        outcome = "Invalid PIN",
                        encryptedPhotoPath = photoPath
                    )
                }
            }
        }
    }

    fun authenticateWithBiometric(activity: FragmentActivity) {
        if (_lockoutSecondsRemaining.value > 0) return
        BiometricAuthHelper.authenticate(
            activity = activity,
            title = "Unlock Dark Room",
            subtitle = "Verify fingerprint to open your private vault",
            negativeButtonText = "Use PIN",
            onSuccess = {
                viewModelScope.launch {
                    prefs.updateConfig { it.copy(failedAttemptCount = 0, lockoutUntilEpochMs = 0L) }
                }
                lastInteractionElapsedMs = SystemClock.elapsedRealtime()
                _pinErrorText.value = null
                _secretVaultSwitchSheetVisible.value = false
                _authState.value = VaultAuthState.Unlocked(VaultScope.MASTER)
            },
            onError = { _, errString ->
                if (errString.isNotBlank()) {
                    _events.tryEmit(VaultEvent.ShowMessage(errString.toString(), isError = true))
                }
            },
            onFailed = {
                viewModelScope.launch {
                    val updated = prefs.updateConfig { it.copy(failedAttemptCount = it.failedAttemptCount + 1) }
                    repository.recordIntruderAttempt(
                        source = "Vault Lock (Biometric)",
                        attemptCount = updated.failedAttemptCount,
                        enteredLength = 0,
                        outcome = "Biometric mismatch",
                        encryptedPhotoPath = null
                    )
                }
            }
        )
    }

    fun onThreeFingerGestureDetected() {
        if (!config.value.threeFingerGestureEnabled) return
        when (val state = _authState.value) {
            is VaultAuthState.NeedsOnboarding -> Unit
            is VaultAuthState.Locked -> {
                _authState.value = state.copy(secretOverlayRevealed = true)
                if (config.value.biometricEnabled &&
                    _biometricAvailability.value == BiometricAvailability.AVAILABLE
                ) {
                    _events.tryEmit(VaultEvent.RequestBiometricOnLock)
                }
                _events.tryEmit(VaultEvent.ShowMessage("Three-finger gesture: Secret Vault prompt opened"))
            }

            is VaultAuthState.Unlocked -> {
                if (state.scope == VaultScope.DECOY) {
                    _secretVaultSwitchSheetVisible.value = true
                } else {
                    _events.tryEmit(VaultEvent.NavigateToVaultHome)
                    _events.tryEmit(VaultEvent.ShowMessage("Secret Vault unlocked · 3-finger gesture verified"))
                }
            }
        }
    }

    fun dismissSecretSwitchSheet() {
        _secretVaultSwitchSheetVisible.value = false
    }

    fun dismissSecretLockOverlay() {
        val current = _authState.value
        if (current is VaultAuthState.Locked && current.stealthCoverActive) {
            _authState.value = current.copy(secretOverlayRevealed = false)
        }
    }

    fun verifyMasterForSecretSwitch(pin: String): Boolean {
        return if (prefs.matchesMasterPin(pin)) {
            _secretVaultSwitchSheetVisible.value = false
            lastInteractionElapsedMs = SystemClock.elapsedRealtime()
            _authState.value = VaultAuthState.Unlocked(VaultScope.MASTER)
            _events.tryEmit(VaultEvent.ShowMessage("Switched to Secret Master Vault"))
            true
        } else {
            false
        }
    }

    fun importMedia(uris: List<Uri>, mediaType: MediaType) {
        val scope = (_authState.value as? VaultAuthState.Unlocked)?.scope ?: return
        if (uris.isEmpty()) return

        viewModelScope.launch {
            var successCount = 0
            val label = when (mediaType) {
                MediaType.PHOTO -> "Encrypting photos..."
                MediaType.VIDEO -> "Encrypting videos..."
                MediaType.AUDIO -> "Encrypting audio..."
            }
            uris.forEachIndexed { index, uri ->
                _importStatus.value = ImportStatus.Encrypting(index + 1, uris.size, label)
                val result = repository.importMediaFromUri(uri, mediaType, scope)
                if (result.isSuccess) successCount++
            }
            _importStatus.value = ImportStatus.Idle
            val typeLabel = when (mediaType) {
                MediaType.PHOTO -> "photo"
                MediaType.VIDEO -> "video"
                MediaType.AUDIO -> "audio file"
            }
            val plural = if (successCount == 1) "" else "s"
            _events.emit(
                VaultEvent.ShowMessage(
                    message = if (successCount > 0) {
                        "$successCount $typeLabel$plural encrypted with AES-256-GCM"
                    } else {
                        "Unable to encrypt selected file$plural"
                    },
                    isError = successCount == 0
                )
            )
        }
    }

    fun exportMedia(item: VaultMediaEntity, destinationUri: Uri) {
        viewModelScope.launch {
            val result = repository.exportDecryptedToUri(item, destinationUri)
            if (result.isSuccess) {
                _events.emit(VaultEvent.ShowMessage("Decrypted copy exported safely"))
            } else {
                _events.emit(VaultEvent.ShowMessage("Export failed: ${result.exceptionOrNull()?.message}", isError = true))
            }
        }
    }

    fun deleteMedia(item: VaultMediaEntity) {
        viewModelScope.launch {
            repository.deleteVaultItem(item)
            _events.emit(VaultEvent.ShowMessage("${item.displayName} shredded from vault"))
        }
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            _isLoadingApps.value = true
            _installedApps.value = repository.loadLaunchableApps(config.value.lockedPackages)
            _isLoadingApps.value = false
        }
    }

    fun toggleAppLocked(app: InstalledAppInfo, locked: Boolean) {
        viewModelScope.launch {
            repository.setPackageLocked(app.packageName, app.appName, locked)
            _installedApps.value = _installedApps.value.map {
                if (it.packageName == app.packageName) it.copy(isLocked = locked) else it
            }
            if (!locked) {
                AppLockSessionCache.grantTemporaryAccess(app.packageName)
            }
        }
    }

    fun setAppLockMasterEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.updateConfig { it.copy(appLockEnabled = enabled) }
            if (!enabled) AppLockSessionCache.clearAll()
        }
    }

    fun clearIntruderLogs() {
        viewModelScope.launch {
            repository.clearIntruderLogs()
            _events.emit(VaultEvent.ShowMessage("Intruder log cleared"))
        }
    }

    fun updateBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.updateConfig { it.copy(biometricEnabled = enabled) }
        }
    }

    fun updateAutoLockDelay(delayOption: AutoLockDelay) {
        viewModelScope.launch {
            prefs.updateConfig { it.copy(autoLockDelayName = delayOption.name) }
        }
    }

    fun updateScreenshotShield(enabled: Boolean) {
        viewModelScope.launch {
            prefs.updateConfig { it.copy(screenshotShieldEnabled = enabled) }
        }
    }

    fun updateStealthCover(enabled: Boolean) {
        viewModelScope.launch {
            prefs.updateConfig { it.copy(stealthCoverEnabled = enabled) }
        }
    }

    fun updateThreeFingerGesture(enabled: Boolean) {
        viewModelScope.launch {
            prefs.updateConfig { it.copy(threeFingerGestureEnabled = enabled) }
        }
    }

    fun updateIntruderSelfie(enabled: Boolean) {
        viewModelScope.launch {
            prefs.updateConfig { it.copy(intruderSelfieEnabled = enabled) }
        }
    }

    fun verifyCurrentMasterPin(pin: String): Boolean = prefs.matchesMasterPin(pin)

    fun changeMasterPin(currentPin: String, newPin: String): Boolean {
        if (!prefs.matchesMasterPin(currentPin)) return false
        if (prefs.matchesDecoyPin(newPin)) return false
        viewModelScope.launch {
            prefs.setMasterPin(newPin)
            _events.emit(VaultEvent.ShowMessage("Master PIN updated"))
        }
        return true
    }

    fun updateDecoyPin(masterPin: String, newDecoyPin: String): Boolean {
        if (!prefs.matchesMasterPin(masterPin)) return false
        if (prefs.matchesMasterPin(newDecoyPin)) return false
        viewModelScope.launch {
            prefs.setDecoyPin(newDecoyPin)
            _events.emit(VaultEvent.ShowMessage("Decoy PIN configured"))
        }
        return true
    }

    fun removeDecoyPin(masterPin: String): Boolean {
        if (!prefs.matchesMasterPin(masterPin)) return false
        viewModelScope.launch {
            prefs.clearDecoyPin()
            _events.emit(VaultEvent.ShowMessage("Decoy PIN removed"))
        }
        return true
    }

    private fun refreshLockoutRemaining() {
        val untilMs = prefs.currentConfig.lockoutUntilEpochMs
        val remSec = ((untilMs - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
        _lockoutSecondsRemaining.value = remSec
        lockoutTickerJob?.cancel()
        if (remSec > 0) {
            lockoutTickerJob = viewModelScope.launch {
                while (_lockoutSecondsRemaining.value > 0) {
                    delay(1000L)
                    val next = ((prefs.currentConfig.lockoutUntilEpochMs - System.currentTimeMillis()) / 1000L)
                        .toInt().coerceAtLeast(0)
                    _lockoutSecondsRemaining.value = next
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                VaultViewModel(app)
            }
        }
    }
}
