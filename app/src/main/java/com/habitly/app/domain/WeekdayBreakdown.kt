package com.habitly.app.domain

import java.time.DayOfWeek

/**
 * How often habits actually get kept on each weekday.
 *
 * Raw counts rather than rates on purpose: a rate needs a denominator of "days the habit was due",
 * which varies per habit and would make the chart quietly wrong. Counts answer the question people
 * actually have — which day of the week do I keep dropping this?
 */
object WeekdayBreakdown {

    fun completionsByWeekday(records: List<CompletionRecord>): Map<DayOfWeek, Int> {
        val counts = DayOfWeek.entries.associateWith { 0 }.toMutableMap()
        records.filter { it.status == CompletionStatus.DONE }.forEach { record ->
            counts[record.date.dayOfWeek] = (counts[record.date.dayOfWeek] ?: 0) + 1
        }
        return counts
    }

    /** The weekday kept least often, ignoring days with no history at all. */
    fun weakestWeekday(counts: Map<DayOfWeek, Int>): DayOfWeek? {
        if (counts.values.all { it == 0 }) return null
        return counts.minByOrNull { it.value }?.key
    }
}
