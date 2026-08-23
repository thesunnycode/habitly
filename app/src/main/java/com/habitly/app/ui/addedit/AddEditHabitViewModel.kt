package com.habitly.app.ui.addedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.domain.FrequencyType
import com.habitly.app.domain.HabitTemplate
import com.habitly.app.domain.FrequencyValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek

class AddEditHabitViewModel(
    private val repository: HabitDataSource,
    habitId: Long?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditUiState(habitId = habitId))
    val uiState: StateFlow<AddEditUiState> = _uiState.asStateFlow()

    fun onNameChange(name: String) {
        _uiState.value = _uiState.value.copy(name = name, nameError = null)
    }

    fun onIconChange(icon: String) { _uiState.value = _uiState.value.copy(icon = icon) }
    fun onColorChange(colorHex: String) { _uiState.value = _uiState.value.copy(colorHex = colorHex) }
    fun onCategoryChange(category: String) { _uiState.value = _uiState.value.copy(category = category) }
    fun onFrequencyTypeChange(type: FrequencyType) { _uiState.value = _uiState.value.copy(frequencyType = type) }
    fun onSpecificDaysChange(days: Set<DayOfWeek>) { _uiState.value = _uiState.value.copy(specificDays = days) }
    fun onIntervalDaysChange(days: Int?) { _uiState.value = _uiState.value.copy(intervalDays = days) }
    fun onTargetCountChange(target: Int?) { _uiState.value = _uiState.value.copy(targetCount = target) }
    fun onUnitChange(unit: String) { _uiState.value = _uiState.value.copy(unit = unit) }
    fun onReminderTimeChange(time: String?) { _uiState.value = _uiState.value.copy(reminderTime = time) }

    /**
     * Applies a ready-made habit. Everything the form asks for arrives at once — name, icon, colour,
     * category and schedule — so creating a first habit is one tap rather than six decisions.
     */
    fun onTemplateSelected(template: HabitTemplate) {
        _uiState.value = _uiState.value.copy(
            name = template.name,
            icon = template.icon,
            colorHex = template.colorHex,
            category = template.category,
            frequencyType = template.frequencyType,
            specificDays = template.specificDays,
            targetCount = template.targetCount,
            unit = template.unit.orEmpty(),
            reminderTime = template.reminderTime,
            nameError = null,
        )
    }

    fun onSave() {
        val state = _uiState.value
        if (!FrequencyValidator.validateName(state.name)) {
            _uiState.value = state.copy(nameError = "Enter a name up to 40 characters")
            return
        }
        if (!FrequencyValidator.isValid(state.frequencyType, state.specificDays, state.intervalDays)) {
            _uiState.value = state.copy(nameError = "Choose at least one day or interval")
            return
        }

        viewModelScope.launch {
            val entity = HabitEntity(
                id = state.habitId ?: 0,
                name = state.name.trim(),
                icon = state.icon,
                colorHex = state.colorHex,
                category = state.category,
                frequencyType = state.frequencyType,
                frequencyDays = state.specificDays.takeIf { it.isNotEmpty() }?.joinToString(",") { it.name },
                intervalDays = state.intervalDays,
                reminderTime = state.reminderTime,
                createdAt = System.currentTimeMillis(),
                isArchived = false,
                targetCount = state.targetCount?.takeIf { it > 1 },
                unit = state.unit.trim().takeIf { it.isNotBlank() },
            )
            if (state.habitId == null) repository.addHabit(entity) else repository.updateHabit(entity)
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }
}
