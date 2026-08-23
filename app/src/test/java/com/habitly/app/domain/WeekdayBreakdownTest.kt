package com.habitly.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WeekdayBreakdownTest {

    // 17 Aug 2026 is a Monday.
    private fun done(day: Int) = CompletionRecord(LocalDate.of(2026, 8, day), CompletionStatus.DONE)

    @Test
    fun `completions are counted against the weekday they fell on`() {
        val counts = WeekdayBreakdown.completionsByWeekday(listOf(done(17), done(24), done(19)))
        assertEquals(2, counts[DayOfWeek.MONDAY])
        assertEquals(1, counts[DayOfWeek.WEDNESDAY])
    }

    @Test
    fun `every weekday appears even with no history, so the chart has seven bars`() {
        val counts = WeekdayBreakdown.completionsByWeekday(emptyList())
        assertEquals(7, counts.size)
        assertEquals(0, counts[DayOfWeek.SUNDAY])
    }

    @Test
    fun `only kept days count, not rest days or misses`() {
        val records = listOf(
            done(17),
            CompletionRecord(LocalDate.of(2026, 8, 18), CompletionStatus.SKIPPED),
            CompletionRecord(LocalDate.of(2026, 8, 19), CompletionStatus.MISSED),
            CompletionRecord(LocalDate.of(2026, 8, 20), CompletionStatus.PARTIAL),
        )
        val counts = WeekdayBreakdown.completionsByWeekday(records)
        assertEquals(1, counts[DayOfWeek.MONDAY])
        assertEquals(0, counts[DayOfWeek.TUESDAY])
        assertEquals(0, counts[DayOfWeek.WEDNESDAY])
        assertEquals(0, counts[DayOfWeek.THURSDAY])
    }

    @Test
    fun `the weakest weekday is the one kept least often`() {
        val counts = WeekdayBreakdown.completionsByWeekday(
            listOf(done(17), done(24), done(18), done(25), done(19)),
        )
        // Monday twice, Tuesday twice, Wednesday once, the rest never — Thursday onwards tie at zero.
        assertEquals(0, counts[DayOfWeek.THURSDAY])
        assertEquals(DayOfWeek.THURSDAY, WeekdayBreakdown.weakestWeekday(counts))
    }

    @Test
    fun `with no history at all there is no weakest weekday to report`() {
        val counts = WeekdayBreakdown.completionsByWeekday(emptyList())
        assertEquals(null, WeekdayBreakdown.weakestWeekday(counts))
    }
}
