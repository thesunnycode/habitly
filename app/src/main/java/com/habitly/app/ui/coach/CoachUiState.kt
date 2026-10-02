package com.habitly.app.ui.coach

import com.habitly.app.ai.AiResult
import com.habitly.app.ai.actions.ValidAction

enum class CoachRole { USER, COACH }

data class CoachMessage(
    val id: Int,
    val role: CoachRole,
    val text: String,
    val actions: List<ValidAction> = emptyList(),
    /** What an applied action did, shown under the bubble in case the snackbar is hidden behind the sheet. */
    val appliedNote: String? = null,
)

data class CoachUiState(
    val messages: List<CoachMessage> = emptyList(),
    val input: String = "",
    val isTyping: Boolean = false,
    val error: AiResult<*>? = null,
    val needsConsent: Boolean = false,
    val remaining: Int? = null,
) {
    val canSend: Boolean get() = input.isNotBlank() && !isTyping
}

sealed interface CoachEvent {
    data class Applied(val message: String, val undo: suspend () -> Unit) : CoachEvent
    data class OpenHabit(val habitId: Long) : CoachEvent
    data object ActionFailed : CoachEvent
}

const val MAX_COACH_INPUT = 300
val COACH_CHIPS = listOf("Plan my day", "Why do I slip?", "Motivate me", "Which habit first?")
