package com.habitly.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.domain.HabitTemplates
import com.habitly.app.domain.toEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The starter habits offered on the last step, in the order they are shown and created. */
val ONBOARDING_TEMPLATE_KEYS = listOf(
    "drink_water", "read_20_minutes", "workout", "meditate",
    "sleep_by_11pm", "morning_run", "journal", "stretch",
)

/** What onboarding writes to preferences; the NavHost adapts `SettingsPrefs` + `OnboardingPrefs`. */
interface OnboardingStore {
    suspend fun setUserName(name: String?)
    suspend fun setCompleted()
}

data class OnboardingUiState(
    val step: Int = 0,
    val name: String = "",
    val selected: Set<String> = emptySet(),
    val isFinished: Boolean = false,
)

class OnboardingViewModel(
    private val repository: HabitDataSource,
    private val prefs: OnboardingStore,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    /** Guards a double tap on Finish/Skip creating the habits twice. */
    private var completing = false

    /** Templates already turned into habits, so retrying after a failure does not add them twice. */
    private val created = mutableSetOf<String>()

    fun onNext() = _uiState.update { it.copy(step = (it.step + 1).coerceAtMost(LAST_STEP)) }

    fun onBack() = _uiState.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }

    fun onNameChange(name: String) = _uiState.update { it.copy(name = name) }

    fun onToggleTemplate(key: String) = _uiState.update {
        it.copy(selected = if (key in it.selected) it.selected - key else it.selected + key)
    }

    /** Leaves onboarding with no habits; a name already typed is still kept. */
    fun onSkip() = complete(createHabits = false)

    fun onFinish() = complete(createHabits = true)

    private fun complete(createHabits: Boolean) {
        if (completing) return
        completing = true
        val state = _uiState.value
        viewModelScope.launch {
            try {
                if (createHabits) {
                    val createdAt = now()
                    ONBOARDING_TEMPLATE_KEYS
                        .filter { it in state.selected && it !in created }
                        .forEach { key ->
                            val template = HabitTemplates.byKey(key) ?: return@forEach
                            repository.addHabit(template.toEntity(createdAt))
                            created += key
                        }
                }
                prefs.setUserName(state.name.trim().ifBlank { null })
                prefs.setCompleted()
                _uiState.update { it.copy(isFinished = true) }
            } catch (e: CancellationException) {
                completing = false
                throw e
            } catch (e: Exception) {
                // A failed write leaves the user on the screen with Finish/Skip still working.
                completing = false
            }
        }
    }

    companion object {
        const val LAST_STEP = 3
    }
}
