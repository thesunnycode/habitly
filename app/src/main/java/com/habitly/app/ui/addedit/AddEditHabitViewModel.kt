package com.habitly.app.ui.addedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.domain.FrequencyType
import com.habitly.app.domain.FrequencyValidator
import com.habitly.app.domain.HabitTemplate
import com.habitly.app.domain.HabitTemplates
import com.habitly.app.ui.theme.HabitPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

class AddEditHabitViewModel(
    private val repository: HabitDataSource,
    habitId: Long?,
    templateKey: String? = null,
    draft: HabitTemplate? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditUiState(habitId = habitId))
    val uiState: StateFlow<AddEditUiState> = _uiState.asStateFlow()

    /** The habit being edited, so saving keeps the fields the form doesn't show (created date, order). */
    private var existing: HabitEntity? = null

    /** The form as it was loaded or prefilled; [AddEditUiState.isDirty] compares against this. */
    private var baseline = formOf(_uiState.value)

    init {
        if (habitId == null) {
            (draft ?: templateKey?.let(HabitTemplates::byKey))?.let(::onTemplateSelected)
            resetBaseline()
        }
        viewModelScope.launch {
            val categories = repository.observeHabitsWithStats(LocalDate.now()).first()
                .map { it.habit.category }
                .filter { it.isNotBlank() }
                .distinct()
            _uiState.value = _uiState.value.copy(existingCategories = categories)
        }
        if (habitId != null) {
            viewModelScope.launch {
                val habit = repository.getHabit(habitId)
                if (habit == null) {
                    // Deleted elsewhere: close rather than let a save create a new habit in its place.
                    _uiState.value = _uiState.value.copy(isNotFound = true)
                    return@launch
                }
                existing = habit
                _uiState.value = _uiState.value.copy(
                    name = habit.name,
                    icon = habit.icon,
                    colorHex = HabitPalette.nearestSeed(habit.colorHex),
                    category = habit.category,
                    frequencyType = habit.frequencyType,
                    specificDays = habit.frequencyDays.orEmpty().split(",")
                        .mapNotNull { name -> DayOfWeek.entries.firstOrNull { it.name == name.trim() } }
                        .toSet(),
                    intervalDays = habit.intervalDays,
                    reminderTime = habit.reminderTime,
                    targetCount = habit.targetCount,
                    unit = habit.unit.orEmpty(),
                )
                resetBaseline()
            }
        }
    }

    fun onNameChange(name: String) {
        val wasFilled = _uiState.value.name.isNotBlank()
        val error = when {
            name.isBlank() && wasFilled -> NAME_REQUIRED
            name.trim().length > MAX_NAME -> NAME_TOO_LONG
            else -> null
        }
        edit { it.copy(name = name, nameError = error) }
    }

    fun onIconChange(icon: String) = edit { it.copy(icon = icon) }
    fun onColorChange(colorHex: String) = edit { it.copy(colorHex = colorHex) }
    fun onCategoryChange(category: String) = edit { it.copy(category = category.trim()) }

    fun onFrequencyTypeChange(type: FrequencyType) = edit {
        // An interval needs a step to be valid; start at the smallest one that isn't simply daily.
        val interval = if (type == FrequencyType.INTERVAL && it.intervalDays == null) 2 else it.intervalDays
        it.copy(frequencyType = type, intervalDays = interval)
    }

    fun onSpecificDaysChange(days: Set<DayOfWeek>) = edit { it.copy(specificDays = days) }
    fun onIntervalDaysChange(days: Int?) = edit { it.copy(intervalDays = days) }
    fun onTargetCountChange(target: Int?) = edit { it.copy(targetCount = target) }
    fun onUnitChange(unit: String) = edit { it.copy(unit = unit) }

    /** [time] is "HH:mm", or null to turn the reminder off. */
    fun onReminderTimeChange(time: String?) = edit { it.copy(reminderTime = time) }

    /**
     * Applies a ready-made habit. Everything the form asks for arrives at once — name, icon, colour,
     * category and schedule — so creating a first habit is one tap rather than six decisions.
     */
    fun onTemplateSelected(template: HabitTemplate) = edit {
        it.copy(
            name = template.name,
            icon = template.icon,
            colorHex = template.colorHex,
            category = template.category,
            frequencyType = template.frequencyType,
            specificDays = template.specificDays,
            intervalDays = template.intervalDays,
            targetCount = template.targetCount,
            unit = template.unit.orEmpty(),
            reminderTime = template.reminderTime,
            nameError = null,
        )
    }

    fun onSave() {
        val state = _uiState.value
        // Editing, but the habit hasn't loaded (or no longer exists): there is nothing to update.
        if (state.habitId != null && existing == null) return
        if (!FrequencyValidator.validateName(state.name)) {
            _uiState.value = state.copy(nameError = if (state.name.isBlank()) NAME_REQUIRED else NAME_TOO_LONG)
            return
        }
        // The Save button is disabled for an incomplete schedule; this only guards a stray call.
        if (!FrequencyValidator.isValid(state.frequencyType, state.specificDays, state.intervalDays)) return

        viewModelScope.launch {
            val base = existing ?: HabitEntity(
                name = "",
                icon = "",
                colorHex = "",
                category = "",
                frequencyType = FrequencyType.DAILY,
                frequencyDays = null,
                intervalDays = null,
                reminderTime = null,
                createdAt = System.currentTimeMillis(),
                isArchived = false,
            )
            val entity = base.copy(
                name = state.name.trim(),
                icon = state.icon,
                colorHex = HabitPalette.nearestSeed(state.colorHex),
                category = state.category.ifBlank { DEFAULT_CATEGORY },
                frequencyType = state.frequencyType,
                frequencyDays = state.specificDays
                    .takeIf { state.frequencyType == FrequencyType.SPECIFIC_DAYS && it.isNotEmpty() }
                    ?.sorted()?.joinToString(",") { it.name },
                intervalDays = state.intervalDays.takeIf { state.frequencyType == FrequencyType.INTERVAL },
                reminderTime = state.reminderTime,
                targetCount = state.targetCount?.takeIf { it > 1 },
                unit = state.unit.trim().takeIf { it.isNotBlank() },
            )
            if (state.habitId == null) repository.addHabit(entity) else repository.updateHabit(entity)
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }

    private fun edit(transform: (AddEditUiState) -> AddEditUiState) {
        val next = transform(_uiState.value)
        _uiState.value = next.copy(isDirty = formOf(next) != baseline)
    }

    private fun resetBaseline() {
        baseline = formOf(_uiState.value)
        _uiState.value = _uiState.value.copy(isDirty = false)
    }

    /** Just the fields the user edits, so validation and loading state never count as changes. */
    private fun formOf(state: AddEditUiState) = state.copy(
        existingCategories = emptyList(),
        nameError = null,
        isDirty = false,
        isSaved = false,
        isNotFound = false,
    )

    private companion object {
        const val MAX_NAME = 40
        const val DEFAULT_CATEGORY = "General"
        const val NAME_REQUIRED = "Give your habit a name"
        const val NAME_TOO_LONG = "Keep the name to 40 characters or fewer"
    }
}
