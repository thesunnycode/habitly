package com.habitly.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// lightColorScheme()/darkColorScheme() only override the roles passed in explicitly — any role
// left unspecified (secondaryContainer, surfaceVariant, outline, etc.) silently falls back to
// Material3's default purple baseline palette. FilterChip's selected state, the bottom nav's
// selection indicator, and other components all read those roles, so every one of them rendered
// lavender/purple instead of our green until every role actually used in this app was specified.
private val LightColors = lightColorScheme(
    primary = SageGreen,
    onPrimary = Color.White,
    primaryContainer = SageGreenPale,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = SageGreenLight,
    onSecondary = Color.White,
    secondaryContainer = SageGreenPale,
    onSecondaryContainer = LightOnPrimaryContainer,
    tertiary = CoralAccent,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEACC),
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = ErrorRed,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = SageGreenLight,
    onPrimary = DarkOnPrimaryContainer,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = SageGreen,
    onSecondary = DarkOnPrimaryContainer,
    secondaryContainer = DarkPrimaryContainer,
    onSecondaryContainer = DarkOnPrimaryContainer,
    tertiary = CoralAccent,
    onTertiary = DarkOnTertiaryContainer,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = ErrorRed,
    onError = Color.White,
)

@Composable
fun HabitlyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = HabitlyTypography, content = content)
}
