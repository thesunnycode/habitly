package com.habitly.app.ml

import java.time.LocalDate

const val FEATURE_COUNT = 20
const val F_RATE7 = 7
const val F_RATE28 = 8
const val F_SAME_WEEKDAY = 9
const val F_STREAK = 10
const val F_MISSED_PREV = 11
const val F_AGE = 12
const val F_REMINDER = 13
const val F_WEEKEND = 14
const val F_MOMENTUM = 15
const val F_SINCE_KEPT = 16
const val F_TREND = 17
const val F_RATE90 = 18
const val F_RESTED_PREV = 19

val FEATURE_NAMES: List<String> = listOf(
    "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
    "Last 7 due days kept", "Last 28 due days kept", "Same weekday, last 20 weeks (smoothed)", "Streak going in",
    "Missed the previous due day", "Habit age", "Has a reminder", "Weekend",
    "Other habits kept, last 3 days", "Days since last kept", "Recent trend",
    "Last 90 due days kept (smoothed)", "Rested the previous due day",
)

class TrainingRow(val habitId: Long, val date: LocalDate, val x: DoubleArray, val y: Int)
