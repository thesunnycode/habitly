package com.habitly.app.ui.addedit

import com.habitly.app.domain.FrequencyType
import java.time.DayOfWeek

data class AddEditUiState(
    val habitId: Long? = null,
    val name: String = "",
    val icon: String = "star",
    val colorHex: String = "#6B9080",
    val category: String = "General",
    val frequencyType: FrequencyType = FrequencyType.DAILY,
    val specificDays: Set<DayOfWeek> = emptySet(),
    val intervalDays: Int? = null,
    val reminderTime: String? = null,
    val nameError: String? = null,
    val isSaved: Boolean = false,
)
