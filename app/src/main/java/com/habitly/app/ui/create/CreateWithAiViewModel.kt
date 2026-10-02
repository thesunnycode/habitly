package com.habitly.app.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitly.app.ai.AiGateway
import com.habitly.app.ai.AiResult
import com.habitly.app.ai.SuggestRequest
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.domain.DEFAULT_CATEGORIES
import com.habitly.app.domain.toEntity
import com.habitly.app.ui.components.HABIT_ICON_KEYS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val EMPTY_MESSAGE = "No new ideas this time — try describing it differently"

class CreateWithAiViewModel(
    private val gateway: AiGateway,
    private val data: HabitDataSource,
    private val allowConsent: suspend () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateWithAiUiState())
    val uiState: StateFlow<CreateWithAiUiState> = _uiState.asStateFlow()

    /** What was last sent, so Try again repeats it even if the field has been edited since. */
    private var lastText: String? = null

    /** The row each ADDED card inserted, so Undo deletes exactly that habit. */
    private val inserted = mutableMapOf<Int, HabitEntity>()

    /** Cards with an add or undo in flight, so a double tap cannot insert twice. */
    private val busy = mutableSetOf<Int>()

    fun onTextChange(text: String) = _uiState.update { it.copy(text = text.take(MAX_PROMPT_CHARS)) }

    fun onChip(chip: String) = onTextChange(chip)

    fun onSuggest() {
        val state = _uiState.value
        if (!state.canSuggest) return
        lastText = state.text.trim()
        send()
    }

    fun onTryAgain() {
        if (_uiState.value.isLoading || lastText == null) return
        send()
    }

    fun onConsentAllowed() {
        viewModelScope.launch {
            allowConsent()
            _uiState.update { it.copy(needsConsent = false) }
            if (lastText != null && !_uiState.value.isLoading) send()
        }
    }

    fun onConsentDismissed() = _uiState.update { it.copy(needsConsent = false) }

    fun onDismiss(key: Int) = setStatus(key, CardStatus.DISMISSED, null)

    fun onAdd(key: Int) {
        viewModelScope.launch { add(key) }
    }

    fun onAddAll() {
        viewModelScope.launch {
            _uiState.value.cards.filter { it.status == CardStatus.NEW }.forEach { add(it.key) }
        }
    }

    fun onUndo(key: Int) {
        val entity = inserted[key] ?: return
        if (_uiState.value.cards.firstOrNull { it.key == key }?.status != CardStatus.ADDED || !busy.add(key)) return
        viewModelScope.launch {
            try {
                data.deleteHabit(entity)
                inserted.remove(key)
                setStatus(key, CardStatus.NEW, null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The habit stays; the card keeps showing "Added" so the user can retry the undo.
            } finally {
                busy.remove(key)
            }
        }
    }

    /** Parks the suggestion for the Add/Edit screen; returns the key to navigate with. */
    fun onEdit(key: Int): String? =
        _uiState.value.cards.firstOrNull { it.key == key }?.let { AiDraftStore.put(it.suggestion.template) }

    private fun send() {
        val text = lastText ?: return
        _uiState.update {
            it.copy(isLoading = true, error = null, emptyMessage = null, needsConsent = false, intent = null, cards = emptyList())
        }
        inserted.clear()
        viewModelScope.launch {
            val existing = try {
                data.observeAllHabits().first().filter { !it.isArchived }.map { it.name }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            val request = SuggestRequest(text, existing, HABIT_ICON_KEYS, DEFAULT_CATEGORIES)
            when (val result = gateway.call { suggestHabits(request) }) {
                is AiResult.Success -> {
                    val suggestions = SuggestionSanitizer.sanitize(result.value, existing, HABIT_ICON_KEYS)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            remaining = result.remaining ?: it.remaining,
                            intent = result.value.intent.takeIf { _ -> suggestions.isNotEmpty() },
                            cards = suggestions.mapIndexed { index, s -> SuggestionCard(index, s) },
                            emptyMessage = if (suggestions.isEmpty()) EMPTY_MESSAGE else null,
                        )
                    }
                }
                is AiResult.ConsentRequired -> _uiState.update { it.copy(isLoading = false, needsConsent = true) }
                else -> _uiState.update { it.copy(isLoading = false, error = result) }
            }
        }
    }

    private suspend fun add(key: Int) {
        val card = _uiState.value.cards.firstOrNull { it.key == key }
        if (card == null || card.status != CardStatus.NEW || !busy.add(key)) return
        try {
            val entity = card.suggestion.template.toEntity(now())
            val id = data.addHabit(entity)
            inserted[key] = entity.copy(id = id)
            setStatus(key, CardStatus.ADDED, id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Nothing was saved; the card stays NEW so the user can tap Add again.
        } finally {
            busy.remove(key)
        }
    }

    private fun setStatus(key: Int, status: CardStatus, habitId: Long?) = _uiState.update { state ->
        state.copy(cards = state.cards.map { if (it.key == key) it.copy(status = status, habitId = habitId) else it })
    }
}
