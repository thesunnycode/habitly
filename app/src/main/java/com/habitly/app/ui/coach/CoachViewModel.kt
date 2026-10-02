package com.habitly.app.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitly.app.ai.AiContextSource
import com.habitly.app.ai.AiGateway
import com.habitly.app.ai.AiResult
import com.habitly.app.ai.ChatMessageDto
import com.habitly.app.ai.CoachRequest
import com.habitly.app.ai.actions.ActionOutcome
import com.habitly.app.ai.actions.AiActionRunner
import com.habitly.app.ai.actions.AiActionValidator
import com.habitly.app.ai.actions.ValidAction
import com.habitly.app.ai.actions.ValidationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val HISTORY_LIMIT = 6
private const val MAX_ACTIONS = 2

/** The proxy accepts history messages up to this many characters. */
private const val MAX_HISTORY_CHARS = 600

class CoachViewModel(
    private val gateway: AiGateway,
    private val context: AiContextSource,
    private val validation: suspend () -> ValidationContext,
    private val runner: AiActionRunner,
    private val allowConsent: suspend () -> Unit,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoachUiState())
    val uiState: StateFlow<CoachUiState> = _uiState.asStateFlow()

    private val _events = Channel<CoachEvent>(Channel.BUFFERED)
    val events: Flow<CoachEvent> = _events.receiveAsFlow()

    /** The history sent with the last request, so Retry repeats it without touching the bubbles. */
    private var lastRequestMessages: List<ChatMessageDto>? = null
    private var nextId = 0
    private var requestJob: Job? = null

    /** Per message: the action last applied from it and how to undo it. Taken once, so Undo runs once. */
    private val undos = mutableMapOf<Int, PendingUndo>()

    private class PendingUndo(val action: ValidAction, val index: Int, val revert: suspend () -> Unit)

    /** Messages with an action in flight, so a double tap cannot apply it twice. */
    private val applying = mutableSetOf<Int>()

    /** Asks for the opening message, once per conversation. */
    fun open() {
        val state = _uiState.value
        if (state.messages.isNotEmpty() || state.isTyping) return
        request(emptyList())
    }

    fun onInputChange(text: String) = _uiState.update { it.copy(input = text.take(MAX_COACH_INPUT)) }

    fun onSend() {
        if (!_uiState.value.canSend) return
        sendUserMessage(_uiState.value.input.trim())
    }

    fun onChip(text: String) {
        if (_uiState.value.isTyping) return
        // A chip is its own message; whatever the user was typing stays in the field.
        sendUserMessage(text, clearInput = false)
    }

    fun onRetry() {
        val messages = lastRequestMessages ?: return
        if (_uiState.value.isTyping) return
        request(messages)
    }

    fun onConsentAllowed() {
        viewModelScope.launch {
            try {
                allowConsent()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(needsConsent = false, error = AiResult.Failed("consent not saved")) }
                return@launch
            }
            _uiState.update { it.copy(needsConsent = false) }
            onRetry()
        }
    }

    fun onConsentDismissed() = _uiState.update { it.copy(needsConsent = false) }

    fun onAction(messageId: Int, action: ValidAction) {
        if (!applying.add(messageId)) return
        viewModelScope.launch {
            try {
                when (val outcome = runner.run(action, today())) {
                    is ActionOutcome.Applied -> {
                        val index = _uiState.value.messages.firstOrNull { it.id == messageId }?.actions?.indexOf(action) ?: 0
                        undos[messageId] = PendingUndo(action, index.coerceAtLeast(0), outcome.undo)
                        updateMessage(messageId) {
                            it.copy(actions = it.actions - action, appliedNote = outcome.message, canUndo = true)
                        }
                        // The snackbar's Undo goes through the same one-shot path as the bubble's.
                        _events.send(CoachEvent.Applied(outcome.message) { undo(messageId) })
                    }
                    is ActionOutcome.Navigate -> _events.send(CoachEvent.OpenHabit(outcome.habitId))
                    ActionOutcome.Failed -> _events.send(CoachEvent.ActionFailed)
                }
            } finally {
                applying.remove(messageId)
            }
        }
    }

    /** Undo from the bubble: reverses the last action applied from [messageId] and offers it again. */
    fun onUndo(messageId: Int) {
        viewModelScope.launch { undo(messageId) }
    }

    private suspend fun undo(messageId: Int) {
        val pending = undos.remove(messageId) ?: return
        try {
            pending.revert()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _events.send(CoachEvent.ActionFailed)
            return
        }
        // Back where it was, so the first suggestion stays the primary button.
        updateMessage(messageId) {
            val actions = it.actions.toMutableList().apply { add(pending.index.coerceAtMost(size), pending.action) }
            it.copy(actions = actions, appliedNote = null, canUndo = false)
        }
    }

    private fun updateMessage(id: Int, change: (CoachMessage) -> CoachMessage) = _uiState.update { state ->
        state.copy(messages = state.messages.map { if (it.id == id) change(it) else it })
    }

    /** Forgets the conversation when the sheet closes; a reply still in flight is dropped. */
    fun reset() {
        requestJob?.cancel()
        requestJob = null
        lastRequestMessages = null
        undos.clear()
        _uiState.value = CoachUiState()
    }

    private fun sendUserMessage(text: String, clearInput: Boolean = true) {
        val message = CoachMessage(nextId++, CoachRole.USER, text)
        val all = _uiState.value.messages + message
        _uiState.update { it.copy(messages = all, input = if (clearInput) "" else it.input) }
        request(
            all.takeLast(HISTORY_LIMIT).map {
                ChatMessageDto(if (it.role == CoachRole.USER) "user" else "coach", it.text.take(MAX_HISTORY_CHARS))
            },
        )
    }

    private fun request(history: List<ChatMessageDto>) {
        lastRequestMessages = history
        _uiState.update { it.copy(isTyping = true, error = null, needsConsent = false) }
        requestJob = viewModelScope.launch {
            val asOf = today()
            val summaries = try {
                context.summaries(asOf)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            val request = CoachRequest(asOf.toString(), summaries, history)
            when (val result = gateway.call { coach(request) }) {
                is AiResult.Success -> {
                    // If the data can't be read, show the reply without buttons rather than unchecked ones.
                    val ctx = try {
                        validation()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                    val actions = ctx?.let { c ->
                        result.value.actions.mapNotNull { AiActionValidator.validate(it, c, asOf) }.take(MAX_ACTIONS)
                    }.orEmpty()
                    _uiState.update {
                        it.copy(
                            isTyping = false,
                            remaining = result.remaining ?: it.remaining,
                            messages = it.messages + CoachMessage(nextId++, CoachRole.COACH, result.value.reply.trim(), actions),
                        )
                    }
                }
                is AiResult.ConsentRequired -> _uiState.update { it.copy(isTyping = false, needsConsent = true) }
                else -> _uiState.update { it.copy(isTyping = false, error = result) }
            }
        }
    }
}
