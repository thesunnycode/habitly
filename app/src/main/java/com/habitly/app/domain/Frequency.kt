package com.habitly.app.domain

enum class FrequencyType { DAILY, SPECIFIC_DAYS, INTERVAL }

data class CompletionRecord(val date: java.time.LocalDate, val completed: Boolean)
