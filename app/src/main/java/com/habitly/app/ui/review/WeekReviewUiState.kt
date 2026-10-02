package com.habitly.app.ui.review

import com.habitly.app.ai.AiResult
import com.habitly.app.ai.actions.ValidAction
import com.habitly.app.ui.stats.DaySlice
import java.time.LocalDate

/** What a story page needs to draw a habit: identity, streak and Monday..Sunday done/due rates. */
data class HabitVisual(
    val id: Long,
    val name: String,
    val iconKey: String,
    val colorHex: String,
    val currentStreak: Int,
    /** Monday..Sunday, done/due over recent weeks; 0 where nothing was due. */
    val weekdayRates: List<Float>,
)

enum class PageKind { SUMMARY, WIN, PATTERN, TIP, CLOSE }

data class ReviewPageUi(
    val kind: PageKind,
    val headline: String,
    val body: String,
    val habit: HabitVisual? = null,
    val action: ValidAction? = null,
    val applied: Boolean = false,
)

data class WeekReviewUiState(
    val weekStart: LocalDate? = null,
    /** e.g. "22–28 Sep" or "29 Sep – 5 Oct". */
    val weekLabel: String = "",
    val pages: List<ReviewPageUi> = emptyList(),
    val stackDays: List<LocalDate> = emptyList(),
    val stacks: Map<LocalDate, List<DaySlice>> = emptyMap(),
    val slotCount: Int = 0,
    val isLoading: Boolean = false,
    val error: AiResult<*>? = null,
    val needsConsent: Boolean = false,
    val notEnoughHistory: Boolean = false,
    val remaining: Int? = null,
)

sealed interface ReviewEvent {
    data class Applied(val message: String, val undo: suspend () -> Unit) : ReviewEvent
    data object ActionFailed : ReviewEvent
}
