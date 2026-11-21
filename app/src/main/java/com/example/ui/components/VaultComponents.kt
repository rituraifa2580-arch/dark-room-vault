package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BurnishedBronze
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoolSlateText
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DeepSlateNavy
import com.example.ui.theme.ElevatedNavyCard
import com.example.ui.theme.GoldWash
import com.example.ui.theme.ImperialGold
import com.example.ui.theme.IvoryText
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.MutedSteelText
import com.example.ui.theme.NavyOutline
import com.example.ui.theme.ObsidianBlack
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ObsidianVaultBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        ObsidianBlack,
                        MidnightNavy,
                        ObsidianBlack
                    )
                )
            )
    ) {
        // Subtle ambient gold radial halo at the top
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            GoldWash,
                            Color.Transparent
                        ),
                        radius = 900f
                    )
                )
        )
        content()
    }
}

/**
 * Detects a simultaneous three-finger touch gesture anywhere inside the wrapped container.
 */
fun Modifier.detectThreeFingerGesture(
    enabled: Boolean,
    onTriggered: () -> Unit
): Modifier = if (!enabled) {
    this
} else {
    this.pointerInput(enabled) {
        awaitPointerEventScope {
            var armed = true
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressedCount = event.changes.count { it.pressed }
                if (pressedCount >= 3 && armed) {
                    armed = false
                    onTriggered()
                } else if (pressedCount == 0) {
                    armed = true
                }
            }
        }
    }
}

@Composable
fun LuxuryPinSection(
    pin: String,
    errorText: String?,
    lockoutSecondsRemaining: Int,
    showBiometricButton: Boolean,
    confirmButtonLabel: String = "Unlock Vault",
    shakeTrigger: Int = 0,
    onDigitPress: (String) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    onBiometricClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(shakeTrigger) {
        if (shakeTrigger > 0) {
            val sequence = listOf(-18f, 18f, -14f, 14f, -8f, 8f, 0f)
            sequence.forEach { target ->
                shakeOffset.animateTo(target, animationSpec = tween(42))
            }
        }
    }

    val isLockedOut = lockoutSecondsRemaining > 0

    Column(
        modifier = modifier.widthIn(max = 380.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 4–6 digit PIN indicator dots
        Row(
            modifier = Modifier
                .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (index in 0 until 6) {
                val isFilled = index < pin.length
                val isRequiredSlot = index < 4
                val dotColor by animateColorAsState(
                    targetValue = when {
                        errorText != null && isFilled -> CrimsonAlert
                        isFilled -> ImperialGold
                        else -> Color.Transparent
                    },
                    animationSpec = tween(150),
                    label = "pinDotColor"
                )
                val borderColor by animateColorAsState(
                    targetValue = when {
                        errorText != null -> CrimsonAlert.copy(alpha = 0.7f)
                        isFilled -> ChampagneGold
                        isRequiredSlot -> ImperialGold.copy(alpha = 0.55f)
                        else -> NavyOutline.copy(alpha = 0.7f)
                    },
                    animationSpec = tween(150),
                    label = "pinDotBorder"
                )
                val scale by animateFloatAsState(
                    targetValue = if (isFilled) 1.15f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "pinDotScale"
                )
                Box(
                    modifier = Modifier
                        .size(if (isRequiredSlot) 16.dp else 14.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(dotColor)
                        .border(1.5.dp, borderColor, CircleShape)
                )
            }
        }

        // Status / Error line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLockedOut -> {
                    Text(
                        text = "Too many attempts · Retry in ${lockoutSecondsRemaining}s",
                        style = MaterialTheme.typography.labelMedium,
                        color = CrimsonAlert,
                        textAlign = TextAlign.Center
                    )
                }
                errorText != null -> {
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CrimsonAlert,
                        textAlign = TextAlign.Center
                    )
                }
                else -> {
                    Text(
                        text = "Enter 4–6 digit PIN (${pin.length}/6)",
                        style = MaterialTheme.typography.labelMedium,
                        color = CoolSlateText
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Numeric Keypad
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9")
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEach { rowDigits ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rowDigits.forEach { digit ->
                        KeypadDigitButton(
                            digit = digit,
                            enabled = !isLockedOut && pin.length < 6,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDigitPress(digit)
                            }
                        )
                    }
                }
            }

            // Bottom row: Biometric | 0 | Backspace
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showBiometricButton) {
                    KeypadActionButton(
                        testTag = "biometric_prompt_button",
                        contentDescription = "Unlock with fingerprint",
                        enabled = !isLockedOut,
                        highlightGold = true,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onBiometricClick()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Fingerprint,
                            contentDescription = "Fingerprint authentication",
                            tint = ImperialGold,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(72.dp))
                }

                KeypadDigitButton(
                    digit = "0",
                    enabled = !isLockedOut && pin.length < 6,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDigitPress("0")
                    }
                )

                KeypadActionButton(
                    testTag = "pin_backspace_button",
                    contentDescription = "Delete digit",
                    enabled = !isLockedOut && pin.isNotEmpty(),
                    highlightGold = false,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onBackspace()
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = if (pin.isNotEmpty()) IvoryText else MutedSteelText,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onSubmit()
            },
            enabled = !isLockedOut && pin.length in 4..6,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("pin_submit_button"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ImperialGold,
                contentColor = ObsidianBlack,
                disabledContainerColor = ElevatedNavyCard,
                disabledContentColor = MutedSteelText
            )
        ) {
            Icon(
                imageVector = Icons.Filled.LockOpen,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = confirmButtonLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun KeypadDigitButton(
    digit: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "keyScale"
    )
    val view = LocalView.current

    Surface(
        modifier = Modifier
            .size(72.dp)
            .scale(scale)
            .minimumInteractiveComponentSize()
            .testTag("pin_key_$digit"),
        shape = CircleShape,
        color = if (isPressed) ElevatedNavyCard else DeepSlateNavy.copy(alpha = 0.85f),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    if (isPressed) ImperialGold else NavyOutline,
                    NavyOutline.copy(alpha = 0.45f)
                )
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.material3.ripple(color = ImperialGold),
                    enabled = enabled,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onClick()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = digit,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) IvoryText else MutedSteelText
            )
        }
    }
}

@Composable
private fun KeypadActionButton(
    testTag: String,
    contentDescription: String,
    enabled: Boolean,
    highlightGold: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(72.dp)
            .minimumInteractiveComponentSize()
            .testTag(testTag),
        shape = CircleShape,
        color = if (highlightGold) GoldWash else DeepSlateNavy.copy(alpha = 0.55f),
        border = BorderStroke(
            width = 1.dp,
            color = if (highlightGold) ImperialGold.copy(alpha = 0.5f) else NavyOutline.copy(alpha = 0.55f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    enabled = enabled,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
    }
}

@Composable
fun SecurityBadgeChip(
    label: String,
    accentColor: Color = ImperialGold,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = accentColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.VerifiedUser,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = accentColor
            )
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var idx = 0
    while (value >= 1024.0 && idx < units.lastIndex) {
        value /= 1024.0
        idx++
    }
    return String.format(Locale.US, if (idx == 0) "%.0f %s" else "%.1f %s", value, units[idx])
}

fun formatDurationMs(durationMs: Long): String {
    if (durationMs <= 0L) return "00:00"
    val totalSeconds = durationMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun formatTimestamp(epochMs: Long): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy · HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(epochMs))
}
