package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.VaultScope
import com.example.security.AutoLockDelay
import com.example.security.BiometricAvailability
import com.example.security.IntruderCameraCapture
import com.example.security.VaultConfigSnapshot
import com.example.ui.components.SecurityBadgeChip
import com.example.ui.components.detectThreeFingerGesture
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoolSlateText
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DeepSlateNavy
import com.example.ui.theme.ElevatedNavyCard
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.GoldWash
import com.example.ui.theme.ImperialGold
import com.example.ui.theme.IvoryText
import com.example.ui.theme.NavyOutline
import com.example.ui.theme.ObsidianBlack

@Composable
fun VaultSettingsScreen(
    vaultScope: VaultScope,
    config: VaultConfigSnapshot,
    biometricAvailability: BiometricAvailability,
    hardwareKeystoreActive: Boolean,
    onUpdateBiometric: (Boolean) -> Unit,
    onUpdateAutoLock: (AutoLockDelay) -> Unit,
    onUpdateScreenshotShield: (Boolean) -> Unit,
    onUpdateStealthCover: (Boolean) -> Unit,
    onUpdateThreeFingerGesture: (Boolean) -> Unit,
    onUpdateIntruderSelfie: (Boolean) -> Unit,
    onChangeMasterPin: (currentPin: String, newPin: String) -> Boolean,
    onUpdateDecoyPin: (masterPin: String, decoyPin: String) -> Boolean,
    onRemoveDecoyPin: (masterPin: String) -> Boolean,
    onLockNow: () -> Unit
) {
    val context = LocalContext.current
    var showChangeMasterDialog by remember { mutableStateOf(false) }
    var showDecoyPinDialog by remember { mutableStateOf(false) }
    var showAutoLockDialog by remember { mutableStateOf(false) }
    var threeFingerVerified by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onUpdateIntruderSelfie(granted)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DeepSlateNavy),
                border = BorderStroke(1.dp, ImperialGold.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SecurityBadgeChip(
                        label = if (hardwareKeystoreActive) "HARDWARE KEYSTORE ACTIVE" else "AES-256-GCM KEYSTORE"
                    )
                    Text(
                        text = "Cryptographic Posture",
                        style = MaterialTheme.typography.titleLarge,
                        color = IvoryText
                    )
                    Text(
                        text = "Private media is encrypted in 256 KB AES-256-GCM authenticated chunks. PINs are salted and PBKDF2-HMAC-SHA256 hashed (120,000 rounds). Zero network permissions declared.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CoolSlateText
                    )
                }
            }
        }

        // Master-only authentication & stealth controls (hidden in Decoy mode for plausible deniability)
        if (vaultScope == VaultScope.MASTER) {
            item {
                Text(
                    text = "AUTHENTICATION & PINS",
                    style = MaterialTheme.typography.labelLarge,
                    color = ChampagneGold
                )
            }

            item {
                SettingsActionCard(
                    icon = Icons.Filled.Key,
                    title = "Change Master PIN",
                    subtitle = "Update your 4–6 digit primary vault PIN",
                    testTag = "change_master_pin_button",
                    onClick = { showChangeMasterDialog = true }
                )
            }

            item {
                SettingsActionCard(
                    icon = Icons.Filled.Key,
                    title = if (config.hasDecoyPin) "Update or Remove Decoy PIN" else "Configure Decoy PIN",
                    subtitle = if (config.hasDecoyPin) {
                        "Decoy vault PIN is armed and isolated"
                    } else {
                        "Set a separate PIN that opens an isolated secondary vault"
                    },
                    testTag = "configure_decoy_pin_button",
                    onClick = { showDecoyPinDialog = true }
                )
            }

            item {
                SettingsToggleCard(
                    icon = Icons.Filled.Fingerprint,
                    title = "Biometric Fingerprint Unlock",
                    subtitle = when (biometricAvailability) {
                        BiometricAvailability.AVAILABLE -> "Use Android BiometricPrompt to unlock Master Vault"
                        BiometricAvailability.NONE_ENROLLED -> "No fingerprint enrolled on device"
                        else -> "Biometric sensor unavailable"
                    },
                    checked = config.biometricEnabled,
                    enabled = biometricAvailability == BiometricAvailability.AVAILABLE,
                    testTag = "settings_biometric_switch",
                    onCheckedChange = onUpdateBiometric
                )
            }

            item {
                Text(
                    text = "STEALTH & GESTURES",
                    style = MaterialTheme.typography.labelLarge,
                    color = ChampagneGold
                )
            }

            item {
                SettingsToggleCard(
                    icon = Icons.Filled.VisibilityOff,
                    title = "Stealth Darkroom Cover",
                    subtitle = "Show an analog exposure meter instead of PIN keypad when locked",
                    checked = config.stealthCoverEnabled,
                    enabled = true,
                    testTag = "settings_stealth_switch",
                    onCheckedChange = onUpdateStealthCover
                )
            }

            item {
                SettingsToggleCard(
                    icon = Icons.Filled.TouchApp,
                    title = "Three-Finger Secret Gesture",
                    subtitle = "Tap with 3 fingers anywhere while active to open Secret Vault prompt",
                    checked = config.threeFingerGestureEnabled,
                    enabled = true,
                    testTag = "settings_three_finger_switch",
                    onCheckedChange = onUpdateThreeFingerGesture
                )
            }

            if (config.threeFingerGestureEnabled) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .detectThreeFingerGesture(enabled = true) {
                                threeFingerVerified = true
                            },
                        shape = RoundedCornerShape(18.dp),
                        color = if (threeFingerVerified) GoldWash else ElevatedNavyCard,
                        border = BorderStroke(
                            1.dp,
                            if (threeFingerVerified) EmeraldVerified else ImperialGold.copy(alpha = 0.4f)
                        )
                    ) {
                        Box(
                            modifier = Modifier.padding(18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (threeFingerVerified) {
                                    "Three-finger gesture verified!"
                                } else {
                                    "Gesture Test Pad: Place 3 fingers here simultaneously"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = if (threeFingerVerified) EmeraldVerified else ChampagneGold
                            )
                        }
                    }
                }
            }

            item {
                SettingsToggleCard(
                    icon = Icons.Filled.CameraFront,
                    title = "Intruder Selfie Capture",
                    subtitle = "Discreetly encrypt a front-camera photo on failed PIN attempts",
                    checked = config.intruderSelfieEnabled,
                    enabled = true,
                    testTag = "settings_intruder_selfie_switch",
                    onCheckedChange = { enabled ->
                        if (enabled && !IntruderCameraCapture.hasCameraPermission(context)) {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        } else {
                            onUpdateIntruderSelfie(enabled)
                        }
                    }
                )
            }
        }

        item {
            Text(
                text = "AUTO-LOCK & DISPLAY SHIELD",
                style = MaterialTheme.typography.labelLarge,
                color = ChampagneGold
            )
        }

        item {
            SettingsActionCard(
                icon = Icons.Filled.LockClock,
                title = "Auto-Lock Timeout",
                subtitle = config.autoLockDelay.label,
                testTag = "settings_autolock_button",
                onClick = { showAutoLockDialog = true }
            )
        }

        item {
            SettingsToggleCard(
                icon = Icons.Filled.ScreenshotMonitor,
                title = "Screenshot & Recents Shield",
                subtitle = "Enforce FLAG_SECURE to block screenshots and hide recent-apps preview",
                checked = config.screenshotShieldEnabled,
                enabled = true,
                testTag = "settings_flag_secure_switch",
                onCheckedChange = onUpdateScreenshotShield
            )
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onLockNow,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("settings_lock_now_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImperialGold,
                    contentColor = ObsidianBlack
                )
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Lock Vault Immediately")
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }

    if (showAutoLockDialog) {
        AlertDialog(
            onDismissRequest = { showAutoLockDialog = false },
            containerColor = DeepSlateNavy,
            title = { Text("Auto-Lock Timeout", color = IvoryText) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AutoLockDelay.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdateAutoLock(option)
                                    showAutoLockDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = config.autoLockDelay == option,
                                onClick = {
                                    onUpdateAutoLock(option)
                                    showAutoLockDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = ImperialGold)
                            )
                            Text(option.label, color = IvoryText)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAutoLockDialog = false }) {
                    Text("Close", color = ChampagneGold)
                }
            }
        )
    }

    if (showChangeMasterDialog) {
        ChangeMasterPinDialog(
            onDismiss = { showChangeMasterDialog = false },
            onConfirm = onChangeMasterPin
        )
    }

    if (showDecoyPinDialog) {
        ConfigureDecoyPinDialog(
            hasExistingDecoy = config.hasDecoyPin,
            onDismiss = { showDecoyPinDialog = false },
            onSaveDecoy = onUpdateDecoyPin,
            onRemoveDecoy = onRemoveDecoyPin
        )
    }
}

@Composable
private fun SettingsActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DeepSlateNavy),
        border = BorderStroke(1.dp, NavyOutline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = ImperialGold, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = IvoryText)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = CoolSlateText)
            }
        }
    }
}

@Composable
private fun SettingsToggleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DeepSlateNavy),
        border = BorderStroke(1.dp, NavyOutline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = ImperialGold, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = IvoryText)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = CoolSlateText)
            }
            Spacer(modifier = Modifier.size(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = Modifier.testTag(testTag),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianBlack,
                    checkedTrackColor = ImperialGold,
                    uncheckedTrackColor = ElevatedNavyCard
                )
            )
        }
    }
}

@Composable
private fun ChangeMasterPinDialog(
    onDismiss: () -> Unit,
    onConfirm: (currentPin: String, newPin: String) -> Boolean
) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeepSlateNavy,
        title = { Text("Change Master PIN", color = IvoryText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PinDialogField(
                    value = currentPin,
                    label = "Current Master PIN",
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) currentPin = it }
                )
                PinDialogField(
                    value = newPin,
                    label = "New Master PIN (4–6 digits)",
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) newPin = it }
                )
                PinDialogField(
                    value = confirmPin,
                    label = "Confirm New Master PIN",
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) confirmPin = it }
                )
                errorText?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = CrimsonAlert)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        newPin.length !in 4..6 -> errorText = "New PIN must be 4 to 6 digits"
                        newPin != confirmPin -> errorText = "New PIN confirmation does not match"
                        !onConfirm(currentPin, newPin) -> errorText = "Incorrect current PIN or matches Decoy PIN"
                        else -> onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ImperialGold, contentColor = ObsidianBlack)
            ) {
                Text("Update PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = CoolSlateText) }
        }
    )
}

@Composable
private fun ConfigureDecoyPinDialog(
    hasExistingDecoy: Boolean,
    onDismiss: () -> Unit,
    onSaveDecoy: (masterPin: String, decoyPin: String) -> Boolean,
    onRemoveDecoy: (masterPin: String) -> Boolean
) {
    var masterPin by remember { mutableStateOf("") }
    var decoyPin by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeepSlateNavy,
        title = { Text("Decoy PIN Configuration", color = IvoryText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PinDialogField(
                    value = masterPin,
                    label = "Verify Master PIN",
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) masterPin = it }
                )
                PinDialogField(
                    value = decoyPin,
                    label = "New Decoy PIN (4–6 digits)",
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) decoyPin = it }
                )
                errorText?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = CrimsonAlert)
                }
                if (hasExistingDecoy) {
                    OutlinedButton(
                        onClick = {
                            if (onRemoveDecoy(masterPin)) {
                                onDismiss()
                            } else {
                                errorText = "Enter valid Master PIN to remove Decoy PIN"
                            }
                        },
                        border = BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Remove Existing Decoy PIN", color = CrimsonAlert)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        decoyPin.length !in 4..6 -> errorText = "Decoy PIN must be 4 to 6 digits"
                        !onSaveDecoy(masterPin, decoyPin) -> errorText = "Invalid Master PIN or Decoy matches Master PIN"
                        else -> onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ImperialGold, contentColor = ObsidianBlack)
            ) {
                Text("Save Decoy PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = CoolSlateText) }
        }
    )
}

@Composable
private fun PinDialogField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ImperialGold,
            unfocusedBorderColor = NavyOutline,
            focusedLabelColor = ChampagneGold,
            unfocusedLabelColor = CoolSlateText,
            focusedTextColor = IvoryText,
            unfocusedTextColor = IvoryText
        )
    )
}
