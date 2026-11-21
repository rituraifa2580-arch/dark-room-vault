package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.security.BiometricAvailability
import com.example.ui.components.LuxuryPinSection
import com.example.ui.components.ObsidianVaultBackground
import com.example.ui.components.SecurityBadgeChip
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoolSlateText
import com.example.ui.theme.DeepSlateNavy
import com.example.ui.theme.ElevatedNavyCard
import com.example.ui.theme.ImperialGold
import com.example.ui.theme.IvoryText
import com.example.ui.theme.NavyOutline
import com.example.ui.theme.ObsidianBlack

private enum class SetupStep {
    CREATE_MASTER,
    CONFIRM_MASTER,
    CREATE_DECOY,
    FINALIZE
}

@Composable
fun OnboardingSetupScreen(
    biometricAvailability: BiometricAvailability,
    hardwareKeystoreActive: Boolean,
    onCompleteSetup: (
        masterPin: String,
        decoyPin: String?,
        enableBiometric: Boolean,
        enableStealthCover: Boolean
    ) -> Unit
) {
    var step by remember { mutableStateOf(SetupStep.CREATE_MASTER) }
    var draftMasterPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var decoyPin by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var shakeTrigger by remember { mutableIntStateOf(0) }

    var enableBiometric by remember {
        mutableStateOf(biometricAvailability == BiometricAvailability.AVAILABLE)
    }
    var enableStealthCover by remember { mutableStateOf(false) }

    BackHandler(enabled = step != SetupStep.CREATE_MASTER) {
        errorText = null
        step = when (step) {
            SetupStep.CREATE_MASTER -> SetupStep.CREATE_MASTER
            SetupStep.CONFIRM_MASTER -> SetupStep.CREATE_MASTER
            SetupStep.CREATE_DECOY -> SetupStep.CONFIRM_MASTER
            SetupStep.FINALIZE -> SetupStep.CREATE_DECOY
        }
    }

    ObsidianVaultBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (step != SetupStep.CREATE_MASTER) {
                    IconButton(
                        onClick = {
                            errorText = null
                            step = when (step) {
                                SetupStep.CONFIRM_MASTER -> SetupStep.CREATE_MASTER
                                SetupStep.CREATE_DECOY -> SetupStep.CONFIRM_MASTER
                                SetupStep.FINALIZE -> SetupStep.CREATE_DECOY
                                else -> SetupStep.CREATE_MASTER
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = IvoryText
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
                SecurityBadgeChip(
                    label = if (hardwareKeystoreActive) "AES-256-GCM · KEYSTORE" else "AES-256-GCM"
                )
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            Image(
                painter = painterResource(id = R.drawable.darkroom_icon_fg),
                contentDescription = "Dark Room Crest",
                modifier = Modifier
                    .size(74.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "DARK ROOM",
                style = MaterialTheme.typography.displayMedium,
                color = ChampagneGold
            )
            Text(
                text = "ZERO-SERVER PRIVATE VAULT",
                style = MaterialTheme.typography.labelMedium,
                color = CoolSlateText
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Step indicator pills
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SetupStep.entries.forEach { item ->
                    val active = item == step
                    val passed = item.ordinal < step.ordinal
                    Box(
                        modifier = Modifier
                            .size(width = if (active) 28.dp else 10.dp, height = 6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                when {
                                    active -> ImperialGold
                                    passed -> ImperialGold.copy(alpha = 0.5f)
                                    else -> NavyOutline
                                }
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            AnimatedContent(targetState = step, label = "setupStep") { currentStep ->
                when (currentStep) {
                    SetupStep.CREATE_MASTER -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.widthIn(max = 420.dp)
                        ) {
                            Text(
                                text = "Create Master PIN",
                                style = MaterialTheme.typography.headlineMedium,
                                color = IvoryText
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Choose a 4 to 6 digit Master PIN. Stored strictly as a salted PBKDF2 hash inside Keystore-encrypted storage.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CoolSlateText,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            LuxuryPinSection(
                                pin = draftMasterPin,
                                errorText = errorText,
                                lockoutSecondsRemaining = 0,
                                showBiometricButton = false,
                                confirmButtonLabel = "Continue",
                                shakeTrigger = shakeTrigger,
                                onDigitPress = { d ->
                                    if (draftMasterPin.length < 6) {
                                        draftMasterPin += d
                                        errorText = null
                                    }
                                },
                                onBackspace = {
                                    if (draftMasterPin.isNotEmpty()) {
                                        draftMasterPin = draftMasterPin.dropLast(1)
                                        errorText = null
                                    }
                                },
                                onSubmit = {
                                    if (draftMasterPin.length in 4..6) {
                                        confirmPin = ""
                                        errorText = null
                                        step = SetupStep.CONFIRM_MASTER
                                    } else {
                                        errorText = "Enter 4 to 6 digits"
                                        shakeTrigger++
                                    }
                                },
                                onBiometricClick = {}
                            )
                        }
                    }

                    SetupStep.CONFIRM_MASTER -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.widthIn(max = 420.dp)
                        ) {
                            Text(
                                text = "Confirm Master PIN",
                                style = MaterialTheme.typography.headlineMedium,
                                color = IvoryText
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Re-enter your ${draftMasterPin.length}-digit Master PIN to verify.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CoolSlateText,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            LuxuryPinSection(
                                pin = confirmPin,
                                errorText = errorText,
                                lockoutSecondsRemaining = 0,
                                showBiometricButton = false,
                                confirmButtonLabel = "Confirm PIN",
                                shakeTrigger = shakeTrigger,
                                onDigitPress = { d ->
                                    if (confirmPin.length < 6) {
                                        confirmPin += d
                                        errorText = null
                                    }
                                },
                                onBackspace = {
                                    if (confirmPin.isNotEmpty()) {
                                        confirmPin = confirmPin.dropLast(1)
                                        errorText = null
                                    }
                                },
                                onSubmit = {
                                    if (confirmPin == draftMasterPin) {
                                        errorText = null
                                        decoyPin = ""
                                        step = SetupStep.CREATE_DECOY
                                    } else {
                                        confirmPin = ""
                                        errorText = "PINs do not match"
                                        shakeTrigger++
                                    }
                                },
                                onBiometricClick = {}
                            )
                        }
                    }

                    SetupStep.CREATE_DECOY -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.widthIn(max = 420.dp)
                        ) {
                            Text(
                                text = "Optional Decoy PIN",
                                style = MaterialTheme.typography.headlineMedium,
                                color = IvoryText
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "A Decoy PIN opens an isolated secondary vault if someone forces you to unlock. Must differ from your Master PIN.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CoolSlateText,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            LuxuryPinSection(
                                pin = decoyPin,
                                errorText = errorText,
                                lockoutSecondsRemaining = 0,
                                showBiometricButton = false,
                                confirmButtonLabel = "Save Decoy PIN",
                                shakeTrigger = shakeTrigger,
                                onDigitPress = { d ->
                                    if (decoyPin.length < 6) {
                                        decoyPin += d
                                        errorText = null
                                    }
                                },
                                onBackspace = {
                                    if (decoyPin.isNotEmpty()) {
                                        decoyPin = decoyPin.dropLast(1)
                                        errorText = null
                                    }
                                },
                                onSubmit = {
                                    if (decoyPin == draftMasterPin) {
                                        decoyPin = ""
                                        errorText = "Decoy PIN cannot match Master PIN"
                                        shakeTrigger++
                                    } else {
                                        errorText = null
                                        step = SetupStep.FINALIZE
                                    }
                                },
                                onBiometricClick = {}
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    decoyPin = ""
                                    errorText = null
                                    step = SetupStep.FINALIZE
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("skip_decoy_button"),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, NavyOutline)
                            ) {
                                Text("Skip Decoy PIN for Now", color = CoolSlateText)
                            }
                        }
                    }

                    SetupStep.FINALIZE -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.widthIn(max = 480.dp)
                        ) {
                            Text(
                                text = "Security Calibration",
                                style = MaterialTheme.typography.headlineMedium,
                                color = IvoryText
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Finalize how Dark Room authenticates and conceals your vault.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CoolSlateText,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))

                            Card(
                                colors = CardDefaults.cardColors(containerColor = DeepSlateNavy),
                                border = BorderStroke(1.dp, NavyOutline),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Fingerprint,
                                            contentDescription = null,
                                            tint = ImperialGold,
                                            modifier = Modifier.size(26.dp)
                                        )
                                        Spacer(modifier = Modifier.size(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Biometric Fingerprint Unlock",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = IvoryText
                                            )
                                            Text(
                                                text = when (biometricAvailability) {
                                                    BiometricAvailability.AVAILABLE -> "Unlock Master Vault via BiometricPrompt"
                                                    BiometricAvailability.NONE_ENROLLED -> "Enroll a fingerprint in Android Settings first"
                                                    else -> "Biometric hardware unavailable on this device"
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = CoolSlateText
                                            )
                                        }
                                        Switch(
                                            checked = enableBiometric,
                                            onCheckedChange = { enableBiometric = it },
                                            enabled = biometricAvailability == BiometricAvailability.AVAILABLE,
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = ObsidianBlack,
                                                checkedTrackColor = ImperialGold,
                                                uncheckedTrackColor = ElevatedNavyCard
                                            )
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.VisibilityOff,
                                            contentDescription = null,
                                            tint = ImperialGold,
                                            modifier = Modifier.size(26.dp)
                                        )
                                        Spacer(modifier = Modifier.size(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Stealth Darkroom Cover",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = IvoryText
                                            )
                                            Text(
                                                text = "Disguise lock screen as a light exposure meter; use a 3-finger tap anywhere to reveal Secret Vault.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = CoolSlateText
                                            )
                                        }
                                        Switch(
                                            checked = enableStealthCover,
                                            onCheckedChange = { enableStealthCover = it },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = ObsidianBlack,
                                                checkedTrackColor = ImperialGold,
                                                uncheckedTrackColor = ElevatedNavyCard
                                            )
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Security,
                                            contentDescription = null,
                                            tint = ImperialGold,
                                            modifier = Modifier.size(26.dp)
                                        )
                                        Spacer(modifier = Modifier.size(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Offline Zero-Knowledge Storage",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = IvoryText
                                            )
                                            Text(
                                                text = "No INTERNET permission. All files are encrypted on-device with AES-256-GCM.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = CoolSlateText
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = {
                                    onCompleteSetup(
                                        draftMasterPin,
                                        decoyPin.takeIf { it.length in 4..6 },
                                        enableBiometric,
                                        enableStealthCover
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("complete_setup_button"),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ImperialGold,
                                    contentColor = ObsidianBlack
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = "Initialize Dark Room Vault",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
