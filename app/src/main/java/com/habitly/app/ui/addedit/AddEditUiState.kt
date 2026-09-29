package com.habitly.app.ui.addedit

import com.habitly.app.domain.FrequencyType
import com.habitly.app.domain.FrequencyValidator
import com.habitly.app.ui.theme.HabitPalette
import java.time.DayOfWeek

data class AddEditUiState(
    val habitId: Long? = null,
    val name: String = "",
    val icon: String = "star",
    val colorHex: String = HabitPalette.seeds.first(),
    val category: String = "General",
    val frequencyType: FrequencyType = FrequencyType.DAILY,
    val specificDays: Set<DayOfWeek> = emptySet(),
    val intervalDays: Int? = null,
    /** "HH:mm", or null when the habit has no reminder. */
    val reminderTime: String? = null,
    /** Units that count as a full day, or null for a plain done/not-done habit. */
    val targetCount: Int? = null,
    val unit: String = "",
    /** Categories already used by the user's habits, offered as chips alongside the defaults. */
    val existingCategories: List<String> = emptyList(),
    val nameError: String? = null,
    /** Whether any form field differs from what was loaded or prefilled. */
    val isDirty: Boolean = false,
    val isSaved: Boolean = false,
    /** Editing a habit that no longer exists; the screen closes. */
    val isNotFound: Boolean = false,
) {
    val canSave: Boolean
        get() = FrequencyValidator.validateName(name) &&
            FrequencyValidator.isValid(frequencyType, specificDays, intervalDays)
}
