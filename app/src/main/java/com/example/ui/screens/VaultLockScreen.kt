package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.LuxuryPinSection
import com.example.ui.components.ObsidianVaultBackground
import com.example.ui.components.SecurityBadgeChip
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoolSlateText
import com.example.ui.theme.DeepSlateNavy
import com.example.ui.theme.ElevatedNavyCard
import com.example.ui.theme.ImperialGold
import com.example.ui.theme.IvoryText
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MutedSteelText
import com.example.ui.theme.NavyOutline
import com.example.ui.theme.ObsidianBlack
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun VaultLockScreen(
    stealthCoverActive: Boolean,
    secretOverlayRevealed: Boolean,
    threeFingerEnabled: Boolean,
    biometricReady: Boolean,
    lockoutSecondsRemaining: Int,
    errorText: String?,
    shakeTrigger: Int,
    onSubmitPin: (String, onCleared: () -> Unit) -> Unit,
    onBiometricClick: () -> Unit,
    onDismissStealthReveal: () -> Unit
) {
    val showPinVault = !stealthCoverActive || secretOverlayRevealed

    LaunchedEffect(showPinVault, biometricReady) {
        if (showPinVault && biometricReady && lockoutSecondsRemaining == 0) {
            onBiometricClick()
        }
    }

    AnimatedContent(targetState = showPinVault, label = "lockOrStealth") { isVaultUnlockVisible ->
        if (isVaultUnlockVisible) {
            StandardVaultLockContent(
                showBackToCover = stealthCoverActive && secretOverlayRevealed,
                threeFingerEnabled = threeFingerEnabled,
                biometricReady = biometricReady,
                lockoutSecondsRemaining = lockoutSecondsRemaining,
                errorText = errorText,
                shakeTrigger = shakeTrigger,
                onSubmitPin = onSubmitPin,
                onBiometricClick = onBiometricClick,
                onBackToCover = onDismissStealthReveal
            )
        } else {
            StealthDarkroomMeterCover(threeFingerEnabled = threeFingerEnabled)
        }
    }
}

@Composable
private fun StandardVaultLockContent(
    showBackToCover: Boolean,
    threeFingerEnabled: Boolean,
    biometricReady: Boolean,
    lockoutSecondsRemaining: Int,
    errorText: String?,
    shakeTrigger: Int,
    onSubmitPin: (String, onCleared: () -> Unit) -> Unit,
    onBiometricClick: () -> Unit,
    onBackToCover: () -> Unit
) {
    var pin by remember { mutableStateOf("") }

    ObsidianVaultBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (showBackToCover) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onBackToCover) {
                        Text("Return to Cover", color = CoolSlateText)
                    }
                }
            }

            Image(
                painter = painterResource(id = R.drawable.darkroom_icon_fg),
                contentDescription = "Dark Room Emblem",
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "DARK ROOM",
                style = MaterialTheme.typography.displayMedium,
                color = ChampagneGold
            )
            Spacer(modifier = Modifier.height(6.dp))
            SecurityBadgeChip(label = "AES-256-GCM ENCRYPTED")

            Spacer(modifier = Modifier.height(24.dp))

            LuxuryPinSection(
                pin = pin,
                errorText = errorText,
                lockoutSecondsRemaining = lockoutSecondsRemaining,
                showBiometricButton = biometricReady,
                confirmButtonLabel = "Unlock Vault",
                shakeTrigger = shakeTrigger,
                onDigitPress = { digit ->
                    if (pin.length < 6) pin += digit
                },
                onBackspace = {
                    if (pin.isNotEmpty()) pin = pin.dropLast(1)
                },
                onSubmit = {
                    onSubmitPin(pin) { pin = "" }
                },
                onBiometricClick = onBiometricClick
            )

            if (threeFingerEnabled) {
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.TouchApp,
                        contentDescription = null,
                        tint = MutedSteelText,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "3-finger gesture active for rapid secret access",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedSteelText
                    )
                }
            }
        }
    }
}

/**
 * Discreet functional darkroom film exposure calculator shown when Stealth Cover Mode is enabled.
 * Placing 3 fingers anywhere on screen reveals the Secret Vault unlock interface.
 */
@Composable
private fun StealthDarkroomMeterCover(
    threeFingerEnabled: Boolean
) {
    val isoOptions = listOf(100, 200, 400, 800, 1600)
    val apertureStops = listOf(1.4f, 2.0f, 2.8f, 4.0f, 5.6f, 8.0f, 11f, 16f)
    var selectedIso by remember { mutableStateOf(400) }
    var apertureIndex by remember { mutableFloatStateOf(2f) }
    var evReading by remember { mutableFloatStateOf(9.0f) }

    val fStop = apertureStops[apertureIndex.roundToInt().coerceIn(apertureStops.indices)]
    val shutterSec = (fStop.toDouble().pow(2.0) / (2.0.pow(evReading.toDouble()) * (selectedIso / 100.0)))
    val shutterLabel = if (shutterSec >= 1.0) {
        String.format("%.1f s", shutterSec)
    } else {
        val denom = (1.0 / shutterSec).roundToInt().coerceAtLeast(1)
        "1/${denom} s"
    }

    ObsidianVaultBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Camera,
                        contentDescription = "Exposure Meter",
                        tint = ImperialGold,
                        modifier = Modifier.size(26.dp)
                    )
                    Column {
                        Text(
                            text = "DARKROOM METER",
                            style = MaterialTheme.typography.titleMedium,
                            color = IvoryText
                        )
                        Text(
                            text = "Analog Film Exposure Calculator",
                            style = MaterialTheme.typography.labelSmall,
                            color = CoolSlateText
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = ElevatedNavyCard,
                    border = BorderStroke(1.dp, NavyOutline)
                ) {
                    Text(
                        text = "ISO $selectedIso",
                        style = MaterialTheme.typography.labelMedium,
                        color = ChampagneGold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Shutter Speed Readout
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                colors = CardDefaults.cardColors(containerColor = DeepSlateNavy),
                border = BorderStroke(1.dp, ImperialGold.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "RECOMMENDED SHUTTER",
                        style = MaterialTheme.typography.labelMedium,
                        color = CoolSlateText
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = shutterLabel,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        color = ChampagneGold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "f/$fStop · EV ${String.format("%.1f", evReading)} · ISO $selectedIso",
                        style = MaterialTheme.typography.labelMedium,
                        color = IvoryText
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                colors = CardDefaults.cardColors(containerColor = DeepSlateNavy),
                border = BorderStroke(1.dp, NavyOutline),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Film Sensitivity (ISO)", style = MaterialTheme.typography.labelLarge, color = IvoryText)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        isoOptions.forEach { iso ->
                            FilterChip(
                                selected = selectedIso == iso,
                                onClick = { selectedIso = iso },
                                label = { Text("$iso") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ImperialGold,
                                    selectedLabelColor = ObsidianBlack,
                                    labelColor = CoolSlateText
                                )
                            )
                        }
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Aperture", style = MaterialTheme.typography.labelLarge, color = IvoryText)
                            Text("f/$fStop", style = MaterialTheme.typography.labelMedium, color = ChampagneGold)
                        }
                        Slider(
                            value = apertureIndex,
                            onValueChange = { apertureIndex = it },
                            valueRange = 0f..(apertureStops.lastIndex.toFloat()),
                            steps = apertureStops.size - 2,
                            colors = SliderDefaults.colors(
                                thumbColor = ImperialGold,
                                activeTrackColor = ImperialGold,
                                inactiveTrackColor = NavyOutline
                            )
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Ambient EV", style = MaterialTheme.typography.labelLarge, color = IvoryText)
                            Text("${String.format("%.1f", evReading)} EV", style = MaterialTheme.typography.labelMedium, color = ChampagneGold)
                        }
                        Slider(
                            value = evReading,
                            onValueChange = { evReading = it },
                            valueRange = 2f..15f,
                            colors = SliderDefaults.colors(
                                thumbColor = ImperialGold,
                                activeTrackColor = ImperialGold,
                                inactiveTrackColor = NavyOutline
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            if (threeFingerEnabled) {
                Box(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "Calibration studio · Three-finger gesture ready",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedSteelText.copy(alpha = 0.65f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
