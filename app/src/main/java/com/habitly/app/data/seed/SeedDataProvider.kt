package com.habitly.app.data.seed

import com.habitly.app.data.local.HabitDao
import com.habitly.app.data.repository.HabitRepository
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.domain.FrequencyType
import kotlinx.coroutines.flow.first
import java.time.LocalDate

object SeedDataProvider {

    private data class SeedHabit(val name: String, val icon: String, val colorHex: String, val category: String)

    private val SEED_HABITS = listOf(
        SeedHabit("Drink Water", "water_drop", "#1098AD", "Health"),
        SeedHabit("Read 20 Minutes", "menu_book", "#F08C00", "Learning"),
        SeedHabit("Workout", "fitness_center", "#F76C5E", "Fitness"),
        SeedHabit("Meditate", "self_improvement", "#AE3EC9", "Wellness"),
    )

    private fun shouldComplete(habitIndex: Int, dayOffset: Int): Boolean {
        val pattern = when (habitIndex) {
            0 -> dayOffset % 7 != 3
            1 -> dayOffset % 2 == 0
            2 -> dayOffset < 5 || dayOffset > 10
            else -> dayOffset % 3 != 0
        }
        return pattern
    }

    suspend fun seedIfEmpty(repository: HabitRepository, habitDao: HabitDao, today: LocalDate) {
        if (habitDao.observeAllHabits().first().isNotEmpty()) return

        SEED_HABITS.forEachIndexed { index, seed ->
            val habitId = repository.addHabit(
                HabitEntity(
                    id = 0,
                    name = seed.name,
                    icon = seed.icon,
                    colorHex = seed.colorHex,
                    category = seed.category,
                    frequencyType = FrequencyType.DAILY,
                    frequencyDays = null,
                    intervalDays = null,
                    reminderTime = "08:00",
                    createdAt = System.currentTimeMillis(),
                    isArchived = false,
                )
            )
            for (dayOffset in 13 downTo 1) {
                val date = today.minusDays(dayOffset.toLong())
                if (shouldComplete(index, dayOffset)) {
                    repository.toggleCompletion(habitId, date, null)
                }
            }
        }
    }
}
