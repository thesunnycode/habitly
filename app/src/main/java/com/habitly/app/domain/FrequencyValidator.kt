package com.habitly.app.domain

import java.time.DayOfWeek

object FrequencyValidator {

    fun isValid(type: FrequencyType, specificDays: Set<DayOfWeek>, intervalDays: Int?): Boolean =
        when (type) {
            FrequencyType.DAILY -> true
            FrequencyType.SPECIFIC_DAYS -> specificDays.isNotEmpty()
            FrequencyType.INTERVAL -> (intervalDays ?: 0) > 0
        }

    fun validateName(name: String): Boolean =
        name.isNotBlank() && name.trim().length <= 40
}
