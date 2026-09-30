package com.habitly.app.ui.theme

import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Every role the app reads is set explicitly — unset roles fall back to Material's purple baseline.
private val DarkScheme = darkColorScheme(
    primary = DarkBrand, onPrimary = DarkBackground,
    primaryContainer = DarkBrandContainer, onPrimaryContainer = DarkBrand,
    secondary = DarkOnSurfaceMuted, onSecondary = DarkBackground,
    secondaryContainer = DarkSurfaceRaised, onSecondaryContainer = DarkOnSurface,
    tertiary = DarkWarning, onTertiary = DarkBackground,
    tertiaryContainer = DarkSurfaceRaised, onTertiaryContainer = DarkWarning,
    background = DarkBackground, onBackground = DarkOnSurface,
    surface = DarkSurface, onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceRaised, onSurfaceVariant = DarkOnSurfaceMuted,
    surfaceContainer = DarkSurface, surfaceContainerHigh = DarkSurfaceRaised,
    surfaceContainerHighest = DarkSurfaceRaised, surfaceContainerLow = DarkSurface,
    surfaceContainerLowest = DarkBackground,
    inverseSurface = DarkOnSurface, inverseOnSurface = DarkBackground,
    outline = DarkOutline, outlineVariant = DarkOutline,
    error = DarkDanger, onError = DarkBackground,
    scrim = Color.Black,
)

private val LightScheme = lightColorScheme(
    primary = LightBrand, onPrimary = LightSurface,
    primaryContainer = LightBrandContainer, onPrimaryContainer = LightBrand,
    secondary = LightOnSurfaceMuted, onSecondary = LightSurface,
    secondaryContainer = LightSurfaceRaised, onSecondaryContainer = LightOnSurface,
    tertiary = LightWarning, onTertiary = LightSurface,
    tertiaryContainer = LightSurfaceRaised, onTertiaryContainer = LightWarning,
    background = LightBackground, onBackground = LightOnSurface,
    surface = LightSurface, onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceRaised, onSurfaceVariant = LightOnSurfaceMuted,
    surfaceContainer = LightSurface, surfaceContainerHigh = LightSurfaceRaised,
    surfaceContainerHighest = LightSurfaceRaised, surfaceContainerLow = LightSurface,
    surfaceContainerLowest = LightBackground,
    inverseSurface = LightOnSurface, inverseOnSurface = LightSurface,
    outline = LightOutline, outlineVariant = LightOutline,
    error = LightDanger, onError = LightSurface,
    scrim = Color.Black,
)

@Composable
fun HabitlyTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    CompositionLocalProvider(
        LocalHabitlyColors provides if (darkTheme) DarkHabitlyColors else LightHabitlyColors,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = HabitlyTypography,
            shapes = HabitlyShapes,
            content = content,
        )
    }
}
