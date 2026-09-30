package com.habitly.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.NavBackStackEntry

private const val TAB_MS = 150
private const val PUSH_MS = 250

private typealias NavScope = AnimatedContentTransitionScope<NavBackStackEntry>

/** Tab to tab is a sideways move between peers; anything else is a screen pushed on top (spec §3.5). */
private fun NavScope.isTabSwitch(): Boolean =
    isTabRoute(initialState.destination.route) && isTabRoute(targetState.destination.route)

/**
 * NavHost transitions: tabs crossfade in 150ms; pushed screens slide in from the trailing edge
 * with a fade over 250ms, and popping reverses it. With reduce motion on, every change is instant.
 */
internal class NavTransitions(private val reduceMotion: Boolean) {

    val enter: NavScope.() -> EnterTransition = {
        when {
            reduceMotion -> EnterTransition.None
            isTabSwitch() -> fadeIn(tween(TAB_MS))
            else -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(PUSH_MS)) +
                fadeIn(tween(PUSH_MS))
        }
    }

    val exit: NavScope.() -> ExitTransition = {
        when {
            reduceMotion -> ExitTransition.None
            isTabSwitch() -> fadeOut(tween(TAB_MS))
            else -> fadeOut(tween(PUSH_MS))
        }
    }

    val popEnter: NavScope.() -> EnterTransition = {
        when {
            reduceMotion -> EnterTransition.None
            isTabSwitch() -> fadeIn(tween(TAB_MS))
            else -> fadeIn(tween(PUSH_MS))
        }
    }

    val popExit: NavScope.() -> ExitTransition = {
        when {
            reduceMotion -> ExitTransition.None
            isTabSwitch() -> fadeOut(tween(TAB_MS))
            else -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(PUSH_MS)) +
                fadeOut(tween(PUSH_MS))
        }
    }
}
