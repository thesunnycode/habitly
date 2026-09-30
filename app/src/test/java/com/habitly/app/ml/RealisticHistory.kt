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

/**
 * Benchmark history (Task 6b), fixed before measuring: four daily habits with base keep rates,
 * weekday effects, a penalty after a not-kept due day, shared rough spells, rest days, and
 * not-kept days that are only sometimes logged as MISSED.
 */
object RealisticHistory {
    /** [trueKeepProb] is the keep probability the generator used for each judged (habitId, date). */
    class Data(
        val habits: List<HabitEntity>,
        val logs: List<HabitLogEntity>,
        val trueKeepProb: Map<Pair<Long, LocalDate>, Double>,
    )

    private class Spec(val name: String, val base: Double, val weekday: Map<DayOfWeek, Double>)

    private val specs = listOf(
        Spec("Water", 0.90, emptyMap()),
        Spec("Read", 0.72, mapOf(DayOfWeek.SATURDAY to -0.30, DayOfWeek.SUNDAY to -0.30)),
        Spec("Workout", 0.65, mapOf(DayOfWeek.MONDAY to -0.35, DayOfWeek.FRIDAY to -0.35)),
        Spec("Meditate", 0.80, mapOf(DayOfWeek.SUNDAY to -0.25)),
    )

    private const val MOMENTUM_PENALTY = -0.20
    private const val SPELL_START = 0.06
    private const val SPELL_PENALTY = -0.35
    private const val REST = 0.03
    private const val MISSED_LOGGED = 0.5

    fun generate(today: LocalDate, days: Int = 150, random: Random = Random(7)): Data {
        val created = today.minusDays(days.toLong())
        val createdAt = created.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val habits = specs.mapIndexed { n, spec ->
            HabitEntity(
                id = n + 1L, name = spec.name, icon = "star",
                colorHex = HabitPalette.seeds[n % HabitPalette.seeds.size], category = "Health",
                frequencyType = FrequencyType.DAILY, frequencyDays = null, intervalDays = null,
                reminderTime = null, createdAt = createdAt, isArchived = false,
            )
        }
        val logs = ArrayList<HabitLogEntity>()
        val truth = HashMap<Pair<Long, LocalDate>, Double>()
        val previousKept = BooleanArray(specs.size) { true } // no penalty on the first due day
        var spellLeft = 0
        var d = created
        while (d.isBefore(today)) {
            // A spell lasts 2-4 days including the day it starts; a new one can only start once it ends.
            if (spellLeft == 0 && random.nextDouble() < SPELL_START) spellLeft = random.nextInt(2, 5)
            val rough = spellLeft > 0
            for ((n, spec) in specs.withIndex()) {
                val habitId = n + 1L
                if (random.nextDouble() < REST) {
                    logs += HabitLogEntity(habitId = habitId, date = d.toString(), status = CompletionStatus.SKIPPED, completedAt = null, note = null)
                    previousKept[n] = false // a rest day is a due day that was not DONE
                    continue
                }
                var p = spec.base + (spec.weekday[d.dayOfWeek] ?: 0.0)
                if (!previousKept[n]) p += MOMENTUM_PENALTY
                if (rough) p += SPELL_PENALTY
                val keepProb = p.coerceIn(0.02, 0.98)
                truth[habitId to d] = keepProb
                val kept = random.nextDouble() < keepProb
                if (kept) {
                    val at = d.atTime(19, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
                    logs += HabitLogEntity(habitId = habitId, date = d.toString(), status = CompletionStatus.DONE, completedAt = at, note = null)
                } else if (random.nextDouble() < MISSED_LOGGED) {
                    logs += HabitLogEntity(habitId = habitId, date = d.toString(), status = CompletionStatus.MISSED, completedAt = null, note = null)
                }
                previousKept[n] = kept
            }
            if (spellLeft > 0) spellLeft--
            d = d.plusDays(1)
        }
        return Data(habits, logs, truth)
    }
}
