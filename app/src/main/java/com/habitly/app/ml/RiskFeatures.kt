package com.habitly.app.ml

import java.time.LocalDate

const val FEATURE_COUNT = 15
const val F_RATE7 = 7
const val F_RATE28 = 8
const val F_SAME_WEEKDAY = 9
const val F_STREAK = 10
const val F_MISSED_PREV = 11
const val F_AGE = 12
const val F_REMINDER = 13
const val F_WEEKEND = 14

val FEATURE_NAMES: List<String> = listOf(
    "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
    "Last 7 due days kept", "Last 28 due days kept", "Same weekday, last 4 weeks", "Streak going in",
    "Missed the previous due day", "Habit age", "Has a reminder", "Weekend",
)

class TrainingRow(val habitId: Long, val date: LocalDate, val x: DoubleArray, val y: Int)
