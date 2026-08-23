package com.habitly.app.data.repository

import com.habitly.app.data.local.HabitEntity

data class HabitWithStats(
    val habit: HabitEntity,
    val currentStreak: Int,
    val bestStreak: Int,
    val completionRate: Float,
    val isCompletedToday: Boolean,
)
