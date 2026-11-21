package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.security.BiometricAvailability
import com.example.ui.components.detectThreeFingerGesture
import com.example.ui.screens.OnboardingSetupScreen
import com.example.ui.screens.VaultLockScreen
import com.example.ui.screens.VaultScaffoldScreen
import com.example.ui.state.VaultAuthState
import com.example.ui.state.VaultEvent
import com.example.ui.state.VaultViewModel
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DarkRoomTheme
import com.example.ui.theme.DeepSlateNavy
import com.example.ui.theme.ImperialGold
import com.example.ui.theme.IvoryText
import kotlinx.coroutines.flow.collectLatest

class MainActivity : FragmentActivity() {

    private val viewModel: VaultViewModel by viewModels { VaultViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                viewModel.onAppForegrounded()
            }

            override fun onStop(owner: LifecycleOwner) {
                viewModel.onAppBackgrounded()
            }
        })

        setContent {
            DarkRoomTheme {
                val config by viewModel.config.collectAsStateWithLifecycle()
                val authState by viewModel.authState.collectAsStateWithLifecycle()
                val allMedia by viewModel.allMedia.collectAsStateWithLifecycle()
                val photos by viewModel.photos.collectAsStateWithLifecycle()
                val videos by viewModel.videos.collectAsStateWithLifecycle()
                val audioTracks by viewModel.audioTracks.collectAsStateWithLifecycle()
                val intruderLogs by viewModel.intruderLogs.collectAsStateWithLifecycle()
                val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
                val isLoadingApps by viewModel.isLoadingApps.collectAsStateWithLifecycle()
                val accessibilityActive by viewModel.accessibilityServiceActive.collectAsStateWithLifecycle()
                val biometricAvailability by viewModel.biometricAvailability.collectAsStateWithLifecycle()
                val importStatus by viewModel.importStatus.collectAsStateWithLifecycle()
                val secretSwitchVisible by viewModel.secretVaultSwitchSheetVisible.collectAsStateWithLifecycle()
                val lockoutSeconds by viewModel.lockoutSecondsRemaining.collectAsStateWithLifecycle()
                val shakeTrigger by viewModel.failedPinShakeTrigger.collectAsStateWithLifecycle()
                val pinErrorText by viewModel.pinErrorText.collectAsStateWithLifecycle()

                val snackbarHostState = remember { SnackbarHostState() }
                var snackbarIsError by remember { mutableIntStateOf(0) }
                var navigateHomeTrigger by remember { mutableIntStateOf(0) }
                val haptic = LocalHapticFeedback.current

                // Dynamically enforce FLAG_SECURE according to user setting
                LaunchedEffect(config.screenshotShieldEnabled) {
                    if (config.screenshotShieldEnabled) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }

                LaunchedEffect(Unit) {
                    viewModel.events.collectLatest { event ->
                        when (event) {
                            is VaultEvent.ShowMessage -> {
                                snackbarIsError = if (event.isError) 1 else 0
                                snackbarHostState.showSnackbar(event.message)
                            }

                            VaultEvent.RequestBiometricOnLock -> {
                                viewModel.authenticateWithBiometric(this@MainActivity)
                            }

                            VaultEvent.NavigateToVaultHome -> {
                                navigateHomeTrigger += 1
                            }
                        }
                    }
                }

                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = {
                        SnackbarHost(hostState = snackbarHostState) { data ->
                            Snackbar(
                                snackbarData = data,
                                containerColor = DeepSlateNavy,
                                contentColor = if (snackbarIsError == 1) CrimsonAlert else IvoryText,
                                actionColor = ChampagneGold
                            )
                        }
                    }
                ) { _ ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent(PointerEventPass.Initial)
                                        viewModel.recordUserInteraction()
                                    }
                                }
                            }
                            .detectThreeFingerGesture(enabled = config.threeFingerGestureEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.onThreeFingerGestureDetected()
                            }
                    ) {
                        when (val activeAuth = authState) {
                            VaultAuthState.NeedsOnboarding -> {
                                OnboardingSetupScreen(
                                    biometricAvailability = biometricAvailability,
                                    hardwareKeystoreActive = viewModel.hardwareKeystoreActive,
                                    onCompleteSetup = { master, decoy, bio, stealth ->
                                        viewModel.completeOnboarding(master, decoy, bio, stealth)
                                    }
                                )
                            }

                            is VaultAuthState.Locked -> {
                                val biometricReady = config.biometricEnabled &&
                                    biometricAvailability == BiometricAvailability.AVAILABLE
                                VaultLockScreen(
                                    stealthCoverActive = activeAuth.stealthCoverActive,
                                    secretOverlayRevealed = activeAuth.secretOverlayRevealed,
                                    threeFingerEnabled = config.threeFingerGestureEnabled,
                                    biometricReady = biometricReady,
                                    lockoutSecondsRemaining = lockoutSeconds,
                                    errorText = pinErrorText,
                                    shakeTrigger = shakeTrigger,
                                    onSubmitPin = { pin, onCleared ->
                                        viewModel.submitLockPin(pin, this@MainActivity, onCleared)
                                    },
                                    onBiometricClick = {
                                        viewModel.authenticateWithBiometric(this@MainActivity)
                                    },
                                    onDismissStealthReveal = {
                                        viewModel.dismissSecretLockOverlay()
                                    }
                                )
                            }

                            is VaultAuthState.Unlocked -> {
                                VaultScaffoldScreen(
                                    vaultScope = activeAuth.scope,
                                    config = config,
                                    allMedia = allMedia,
                                    photos = photos,
                                    videos = videos,
                                    audioTracks = audioTracks,
                                    intruderLogs = intruderLogs,
                                    installedApps = installedApps,
                                    isLoadingApps = isLoadingApps,
                                    accessibilityServiceActive = accessibilityActive,
                                    biometricAvailability = biometricAvailability,
                                    hardwareKeystoreActive = viewModel.hardwareKeystoreActive,
                                    importStatus = importStatus,
                                    secretSwitchSheetVisible = secretSwitchVisible,
                                    navigateHomeTrigger = navigateHomeTrigger,
                                    repository = viewModel.repository,
                                    cryptoEngine = viewModel.cryptoEngine,
                                    onImportMedia = viewModel::importMedia,
                                    onExportMedia = viewModel::exportMedia,
                                    onDeleteMedia = viewModel::deleteMedia,
                                    onLoadInstalledApps = viewModel::loadInstalledApps,
                                    onSetAppLockEnabled = viewModel::setAppLockMasterEnabled,
                                    onToggleAppLocked = viewModel::toggleAppLocked,
                                    onClearIntruderLogs = viewModel::clearIntruderLogs,
                                    onUpdateBiometric = viewModel::updateBiometricEnabled,
                                    onUpdateAutoLock = viewModel::updateAutoLockDelay,
                                    onUpdateScreenshotShield = viewModel::updateScreenshotShield,
                                    onUpdateStealthCover = viewModel::updateStealthCover,
                                    onUpdateThreeFingerGesture = viewModel::updateThreeFingerGesture,
                                    onUpdateIntruderSelfie = viewModel::updateIntruderSelfie,
                                    onChangeMasterPin = viewModel::changeMasterPin,
                                    onUpdateDecoyPin = viewModel::updateDecoyPin,
                                    onRemoveDecoyPin = viewModel::removeDecoyPin,
                                    onVerifyMasterForSecretSwitch = viewModel::verifyMasterForSecretSwitch,
                                    onBiometricSwitchToMaster = {
                                        viewModel.authenticateWithBiometric(this@MainActivity)
                                    },
                                    onDismissSecretSwitchSheet = viewModel::dismissSecretSwitchSheet,
                                    onLockVault = { viewModel.lockVault() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
