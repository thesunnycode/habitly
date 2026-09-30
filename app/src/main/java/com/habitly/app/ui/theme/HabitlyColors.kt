package com.habitly.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Roles Material 3 has no slot for. Read with `LocalHabitlyColors.current`. */
@Immutable
data class HabitlyColors(
    val isDark: Boolean,
    val surfaceRaised: Color,
    val gridEmpty: Color,
    val onSurfaceFaint: Color,
    val heroContainer: Color,
    val success: Color,
    val warning: Color,
    val primaryButton: Color,
    val onPrimaryButton: Color,
)

val DarkHabitlyColors = HabitlyColors(
    isDark = true,
    surfaceRaised = DarkSurfaceRaised,
    gridEmpty = DarkGridEmpty,
    onSurfaceFaint = DarkOnSurfaceFaint,
    heroContainer = DarkHeroContainer,
    success = DarkSuccess,
    warning = DarkWarning,
    primaryButton = DarkOnSurface,
    onPrimaryButton = DarkBackground,
)

val LightHabitlyColors = HabitlyColors(
    isDark = false,
    surfaceRaised = LightSurfaceRaised,
    gridEmpty = LightGridEmpty,
    onSurfaceFaint = LightOnSurfaceFaint,
    heroContainer = LightHeroContainer,
    success = LightSuccess,
    warning = LightWarning,
    primaryButton = LightOnSurface,
    onPrimaryButton = LightSurface,
)

val LocalHabitlyColors = staticCompositionLocalOf { DarkHabitlyColors }

/** True when the system animator scale is 0 — skip confetti, count-ups and bounces. */
val LocalReduceMotion = staticCompositionLocalOf { false }
