package com.habitly.app.ml

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.exp
import kotlin.math.roundToInt

object RiskExplainer {
    private const val MIN_CONTRIBUTION = 0.05

    fun reason(model: RiskModel, x: DoubleArray, date: LocalDate): String {
        val c = model.contributions(x)
        val z = DoubleArray(FEATURE_COUNT) { model.standardized(x, it) }
        for (i in c.indices.sortedByDescending { c[it] }) {
            if (c[i] <= MIN_CONTRIBUTION) break
            riskReason(i, x, z, date)?.let { return it }
        }
        for (i in c.indices.sortedBy { c[it] }) {
            if (c[i] >= -MIN_CONTRIBUTION) break
            when {
                i == F_STREAK && streakOf(x) >= 2 -> return "on a ${streakOf(x)}-day roll"
                i == F_RATE7 && z[i] > 0 -> return "a strong last week"
                i == F_RATE28 && z[i] > 0 -> return "a strong month"
                i == F_MOMENTUM && z[i] > 0 -> return "you're on a good run lately"
                i == F_RATE90 && z[i] > 0 -> return "a strong few months"
                i == F_TREND && z[i] > 0 -> return "you're picking it back up"
            }
        }
        return "you're on track"
    }

    private fun riskReason(i: Int, x: DoubleArray, z: DoubleArray, date: LocalDate): String? = when {
        i in 0..6 -> if (x[i] == 1.0) "you often miss ${plural(DayOfWeek.of(i + 1))}" else null
        i == F_RATE7 && z[i] < 0 -> "a patchy last week"
        i == F_RATE28 && z[i] < 0 -> "it's slipped this month"
        i == F_SAME_WEEKDAY && z[i] < 0 -> "${plural(date.dayOfWeek)} have been hard lately"
        i == F_STREAK && z[i] < 0 -> "no streak going yet"
        i == F_MISSED_PREV && x[i] == 1.0 -> "missed last time"
        i == F_AGE && z[i] < 0 -> "still a new habit"
        i == F_REMINDER && x[i] == 0.0 -> "no reminder set"
        i == F_WEEKEND && x[i] == 1.0 -> "weekends are harder"
        i == F_MOMENTUM && z[i] < 0 -> "the last few days have been tough"
        i == F_SINCE_KEPT && z[i] > 0 && daysSinceKept(x) >= 2 -> "it's been ${daysSinceKept(x)} days since you last did it"
        i == F_TREND && z[i] < 0 -> "it's been slipping lately"
        i == F_RATE90 && z[i] < 0 -> "it's been hard to keep up for a while"
        i == F_RESTED_PREV && x[i] == 1.0 -> "coming back from a rest day"
        else -> null
    }

    private fun streakOf(x: DoubleArray) = (exp(x[F_STREAK]) - 1.0).roundToInt()
    private fun daysSinceKept(x: DoubleArray) = (exp(x[F_SINCE_KEPT]) - 1.0).roundToInt()
    private fun plural(day: DayOfWeek) = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + "s"
}
