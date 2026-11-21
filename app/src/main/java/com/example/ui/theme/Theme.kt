package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkRoomColorScheme = darkColorScheme(
    primary = ImperialGold,
    onPrimary = ObsidianBlack,
    primaryContainer = BurnishedBronze,
    onPrimaryContainer = IvoryText,
    secondary = ChampagneGold,
    onSecondary = ObsidianBlack,
    secondaryContainer = ElevatedNavyCard,
    onSecondaryContainer = ChampagneGold,
    tertiary = EmeraldVerified,
    onTertiary = ObsidianBlack,
    background = ObsidianBlack,
    onBackground = IvoryText,
    surface = MidnightNavy,
    onSurface = IvoryText,
    surfaceVariant = DeepSlateNavy,
    onSurfaceVariant = CoolSlateText,
    surfaceContainerLow = MidnightNavy,
    surfaceContainer = DeepSlateNavy,
    surfaceContainerHigh = ElevatedNavyCard,
    outline = NavyOutline,
    outlineVariant = Color(0xFF1A2640),
    error = CrimsonAlert,
    onError = IvoryText,
    errorContainer = CrimsonWash,
    onErrorContainer = CrimsonAlert
)

private val DarkRoomShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun DarkRoomTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkRoomColorScheme,
        typography = DarkRoomTypography,
        shapes = DarkRoomShapes,
        content = content
    )
}
