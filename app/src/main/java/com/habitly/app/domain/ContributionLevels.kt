package com.habitly.app.domain

import java.time.LocalDate

/**
 * Turns history into the 0..4 shading steps a contribution graph draws.
 *
 * Kept out of the UI so the banding rules are testable: an off-by-one here is the difference between
 * a graph that reads as "most days" and one that reads as "barely ever".
 */
object ContributionLevels {

    const val MAX = 4

    /**
     * One habit's own history. A kept day is full strength; a declared rest day gets the faintest
     * step so it is visible as *something* rather than reading as a failure.
     */
    fun forHabit(records: List<CompletionRecord>): Map<LocalDate, Int> =
        records.mapNotNull { record ->
            when (record.status) {
                CompletionStatus.DONE -> record.date to MAX
                // Partial progress earns a middling shade: real effort, short of the goal.
                CompletionStatus.PARTIAL -> record.date to 2
                CompletionStatus.SKIPPED -> record.date to 1
                CompletionStatus.MISSED -> null
            }
        }.toMap()

    /**
     * Every habit at once: the more of the day's habits were kept, the stronger the square. Bands
     * are proportional so a day with 2 of 2 done reads as strongly as 8 of 8.
     */
    fun forAllHabits(completionsByDate: Map<LocalDate, Int>, habitCount: Int): Map<LocalDate, Int> {
        if (habitCount <= 0) return emptyMap()
        return completionsByDate
            .filterValues { it > 0 }
            .mapValues { (_, done) ->
                val fraction = done.toFloat() / habitCount
                when {
                    fraction >= 1f -> MAX
                    fraction >= 0.66f -> 3
                    fraction >= 0.33f -> 2
                    else -> 1
                }
            }
    }
}
