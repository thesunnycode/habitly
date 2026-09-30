package com.habitly.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.theme.LocalReduceMotion

/**
 * The app-wide snackbar host, provided by HabitlyScaffold so any screen can show a message above
 * the nav bar. The default only exists so previews and tests compose without the scaffold.
 */
val LocalSnackbarHostState = staticCompositionLocalOf { SnackbarHostState() }

/** Roughly one snackbar plus its margin: how far a floating button rises to stay clear of it. */
private val SNACKBAR_CLEARANCE = 72.dp

/**
 * How far a bottom-anchored floating button should move up while a snackbar is showing, so the
 * message never covers it. Animated, or snapped under reduce motion. Apply as `offset(y = -lift)`.
 */
@Composable
fun snackbarLift(): Dp {
    val showing = LocalSnackbarHostState.current.currentSnackbarData != null
    val lift by animateDpAsState(
        targetValue = if (showing) SNACKBAR_CLEARANCE else 0.dp,
        animationSpec = if (LocalReduceMotion.current) snap() else spring(),
        label = "snackbarLift",
    )
    return lift
}
