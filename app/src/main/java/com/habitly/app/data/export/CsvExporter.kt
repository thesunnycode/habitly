package com.habitly.app.data.export

import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.local.HabitLogEntity
import java.time.LocalDate

/** Every logged day of every habit as RFC 4180 CSV, one row per log. */
object CsvExporter {

    private const val HEADER = "habit,category,date,status,progress,unit,note"

    fun export(habits: List<HabitEntity>, logs: List<HabitLogEntity>): String {
        val byId = habits.associateBy { it.id }
        val rows = logs.mapNotNull { log -> byId[log.habitId]?.let { it to log } }
            .sortedWith(compareBy<Pair<HabitEntity, HabitLogEntity>> { it.first.name }.thenBy { it.second.date })
        return buildString {
            append(HEADER).append('\n')
            rows.forEach { (habit, log) ->
                listOf(
                    habit.name, habit.category, log.date, log.status.name,
                    log.progress.toString(), habit.unit.orEmpty(), log.note.orEmpty(),
                ).joinTo(this, separator = ",") { quote(it) }
                append('\n')
            }
        }
    }

    fun fileName(today: LocalDate): String = "habitly-export-$today.csv"

    private fun quote(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${value.replace("\"", "\"\"")}\"" else value
}
