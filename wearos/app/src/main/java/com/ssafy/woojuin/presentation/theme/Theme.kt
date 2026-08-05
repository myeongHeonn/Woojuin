package com.ssafy.woojuin.presentation.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

private val WoojuinColorScheme = ColorScheme(
    primary = WoojuinColor.AccentPurple,
    primaryDim = WoojuinColor.AccentPurple.copy(alpha = 0.8f),
    primaryContainer = WoojuinColor.SurfaceActive,
    onPrimary = WoojuinColor.SpaceBlack,
    onPrimaryContainer = WoojuinColor.StarLavender,
    secondary = WoojuinColor.StarBlue,
    secondaryDim = WoojuinColor.StarBlue.copy(alpha = 0.8f),
    secondaryContainer = WoojuinColor.SurfaceRaised,
    onSecondary = WoojuinColor.SpaceBlack,
    onSecondaryContainer = WoojuinColor.TextPrimary,
    tertiary = WoojuinColor.StarGreen,
    tertiaryDim = WoojuinColor.StarGreen.copy(alpha = 0.8f),
    tertiaryContainer = WoojuinColor.SurfaceRaised,
    onTertiary = WoojuinColor.SpaceBlack,
    onTertiaryContainer = WoojuinColor.TextPrimary,
    surfaceContainerLow = WoojuinColor.SidebarBlack,
    surfaceContainer = WoojuinColor.Surface,
    surfaceContainerHigh = WoojuinColor.SurfaceRaised,
    onSurface = WoojuinColor.TextPrimary,
    onSurfaceVariant = WoojuinColor.TextSecondary,
    outline = WoojuinColor.Border,
    outlineVariant = WoojuinColor.Border,
    background = WoojuinColor.SpaceBlack,
    onBackground = WoojuinColor.TextPrimary,
)

@Composable
fun WoojuinTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WoojuinColorScheme,
        content = content
    )
}
