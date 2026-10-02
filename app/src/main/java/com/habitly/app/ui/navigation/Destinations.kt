package com.habitly.app.ui.navigation

sealed class Destination(val route: String) {
    data object Onboarding : Destination("onboarding")
    data object Today : Destination("today")
    data object Habits : Destination("habits?segment={segment}") {
        const val BASE = "habits"
        fun createRoute(segment: String = "active") = "habits?segment=$segment"
    }
    data object Insights : Destination("insights?tab={tab}") {
        const val BASE = "insights"
        fun createRoute(tab: String = "stats") = "insights?tab=$tab"
    }
    data object You : Destination("you")
    data object Awards : Destination("awards")
    data object Settings : Destination("settings")
    data object Reminders : Destination("reminders")
    data object CreateWithAi : Destination("create_with_ai")
    data object WeekReview : Destination("week_review")
    data object AddEditHabit : Destination("add_edit_habit?habitId={habitId}&template={template}&draft={draft}") {
        fun createRoute(habitId: Long? = null, template: String? = null, draft: String? = null) =
            "add_edit_habit?habitId=${habitId ?: -1L}&template=${template.orEmpty()}&draft=${draft.orEmpty()}"
    }
    data object HabitDetail : Destination("habit_detail/{habitId}") {
        fun createRoute(habitId: Long) = "habit_detail/$habitId"
    }
}

/** Tab routes as registered in the NavHost (patterns), in bar order. */
val TAB_DESTINATIONS = listOf(Destination.Today, Destination.Habits, Destination.Insights, Destination.You)

fun isTabRoute(route: String?): Boolean = TAB_DESTINATIONS.any { it.route == route }
