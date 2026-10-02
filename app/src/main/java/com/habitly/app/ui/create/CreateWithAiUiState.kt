package com.habitly.app.ui.create

import com.habitly.app.ai.AiResult

enum class CardStatus { NEW, ADDED, DISMISSED }

data class SuggestionCard(
    val key: Int,
    val suggestion: Suggestion,
    val status: CardStatus = CardStatus.NEW,
    /** Id of the habit this card created, while it is [CardStatus.ADDED]; needed to undo it. */
    val habitId: Long? = null,
)

data class CreateWithAiUiState(
    val text: String = "",
    val isLoading: Boolean = false,
    val error: AiResult<*>? = null,
    /** Set instead of [error] when the model answered but nothing new survived cleaning. */
    val emptyMessage: String? = null,
    val needsConsent: Boolean = false,
    val intent: String? = null,
    val cards: List<SuggestionCard> = emptyList(),
    val remaining: Int? = null,
) {
    val canSuggest: Boolean get() = text.trim().length >= 3 && !isLoading
    val visibleCards: List<SuggestionCard> get() = cards.filter { it.status != CardStatus.DISMISSED }
    val hasNew: Boolean get() = cards.any { it.status == CardStatus.NEW }
}

const val MAX_PROMPT_CHARS = 300
val PROMPT_CHIPS = listOf("Sleep better", "Get fit", "Less screen time", "Read more", "Drink more water", "Feel calmer")
