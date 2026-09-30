package com.habitly.app.ml

import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.local.HabitLogEntity
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.FrequencyType
import com.habitly.app.ui.theme.HabitPalette
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random

/** Test history generator: daily habits that are always missed on Mondays and kept ~90% of other days. */
object SyntheticHistory {
    fun mondaySkipper(today: LocalDate, days: Int, habits: Int, random: Random): Pair<List<HabitEntity>, List<HabitLogEntity>> {
        val created = today.minusDays(days.toLong())
        val createdAt = created.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val habitList = (1..habits).map { n ->
            HabitEntity(
                id = n.toLong(), name = "Habit $n", icon = "star",
                colorHex = HabitPalette.seeds[(n - 1) % HabitPalette.seeds.size], category = "Health",
                frequencyType = FrequencyType.DAILY, frequencyDays = null, intervalDays = null,
                reminderTime = null, createdAt = createdAt, isArchived = false,
            )
        }
        val logs = ArrayList<HabitLogEntity>()
        for (habit in habitList) {
            var d = created
            while (d.isBefore(today)) {
                if (d.dayOfWeek == DayOfWeek.MONDAY) {
                    logs += HabitLogEntity(habitId = habit.id, date = d.toString(), status = CompletionStatus.MISSED, completedAt = null, note = null)
                } else if (random.nextDouble() >= 0.1) {
                    val at = d.atTime(19, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
                    logs += HabitLogEntity(habitId = habit.id, date = d.toString(), status = CompletionStatus.DONE, completedAt = at, note = null)
                }
                d = d.plusDays(1)
            }
        }
        return habitList to logs
    }
}
