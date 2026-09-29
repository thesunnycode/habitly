package com.habitly.app.ui.navigation

sealed class Destination(val route: String) {
    data object Onboarding : Destination("onboarding")
    data object Home : Destination("home")
    data object Stats : Destination("stats")
    data object Calendar : Destination("calendar")
    data object Achievements : Destination("achievements")
    data object Settings : Destination("settings")
    data object AllHabits : Destination("all_habits")
    data object AddEditHabit : Destination("add_edit_habit?habitId={habitId}") {
        fun createRoute(habitId: Long? = null) = "add_edit_habit?habitId=${habitId ?: -1L}"
    }
    data object HabitDetail : Destination("habit_detail/{habitId}") {
        fun createRoute(habitId: Long) = "habit_detail/$habitId"
    }
}

val BOTTOM_NAV_DESTINATIONS = listOf(
    Destination.Home, Destination.Stats, Destination.Calendar,
    Destination.Achievements, Destination.Settings,
)
