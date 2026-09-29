package com.habitly.app.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class StatsGridMathTest {
    private val today = LocalDate.of(2026, 9, 30) // a Wednesday
    private val month = (29 downTo 0).map { today.minusDays(it.toLong()) } // 1 Sep (Tue) .. 30 Sep
    private val week = (6 downTo 0).map { today.minusDays(it.toLong()) } // Thu 24 .. Wed 30

    @Test fun `a month window is laid out in whole weeks aligned to a Monday week start`() {
        val rows = StatsGridMath.weekRows(month, DayOfWeek.MONDAY, alignToWeeks = true)
        assertEquals(5, rows.size)
        assertTrue(rows.all { it.size == 7 })
        // Mon 31 Aug is outside the window, so the first cell is blank and 1 Sep sits under Tuesday.
        assertEquals(listOf(null, LocalDate.of(2026, 9, 1)), rows[0].take(2))
        // Today is a Wednesday: Thu..Sun of the last row are blank.
        assertEquals(listOf(today, null, null, null, null), rows[4].drop(2))
        assertEquals(month, rows.flatten().filterNotNull())
    }

    @Test fun `a Sunday week start shifts the alignment`() {
        val rows = StatsGridMath.weekRows(month, DayOfWeek.SUNDAY, alignToWeeks = true)
        assertEquals(listOf(null, null, LocalDate.of(2026, 9, 1)), rows[0].take(3))
        assertEquals(DayOfWeek.SUNDAY, rows[1][0]!!.dayOfWeek)
    }

    @Test fun `the rolling week is one row of seven, oldest first`() {
        val rows = StatsGridMath.weekRows(week, DayOfWeek.MONDAY, alignToWeeks = false)
        assertEquals(listOf(week), rows)
    }

    @Test fun `an empty window has no rows`() {
        assertEquals(emptyList<List<LocalDate?>>(), StatsGridMath.weekRows(emptyList(), DayOfWeek.MONDAY, true))
    }

    @Test fun `header follows the week start when aligned and the days themselves when rolling`() {
        assertEquals(
            listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
            StatsGridMath.headerDays(month, DayOfWeek.SUNDAY, alignToWeeks = true).take(3),
        )
        assertEquals(
            listOf(DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            StatsGridMath.headerDays(week, DayOfWeek.MONDAY, alignToWeeks = false).take(2),
        )
        assertEquals(7, StatsGridMath.headerDays(week, DayOfWeek.MONDAY, false).size)
    }

    @Test fun `kept slices stack from the bottom in chip order and rest days sit on top`() {
        val water = DaySlice("#4DABF7", rested = false)
        val readRest = DaySlice("#9775FA", rested = true)
        val run = DaySlice("#FF922B", rested = false)
        assertEquals(listOf(water, run, readRest), StatsGridMath.stackOrder(listOf(water, readRest, run)))
    }

    @Test fun `slice bands split the cell equally with gaps, bottom slice first`() {
        val bands = StatsGridMath.sliceBands(count = 2, height = 10f, gap = 1f)
        assertEquals(listOf(5.5f to 10f, 0f to 4.5f), bands)
        assertEquals(listOf(0f to 10f), StatsGridMath.sliceBands(count = 1, height = 10f, gap = 1f))
        assertEquals(emptyList<Pair<Float, Float>>(), StatsGridMath.sliceBands(count = 0, height = 10f, gap = 1f))
    }

    @Test fun `summary counts days with a habit kept, ignoring rest-only days`() {
        val stacks = mapOf(
            month[0] to listOf(DaySlice("#4DABF7", rested = false)),
            month[1] to listOf(DaySlice("#4DABF7", rested = true)),
            month[2] to listOf(DaySlice("#4DABF7", rested = true), DaySlice("#9775FA", rested = false)),
        )
        assertEquals(
            "Last 30 days: 2 of 30 days with a habit done",
            StatsGridMath.allSummary(month, stacks),
        )
    }

    @Test fun `habit summary counts full-strength days`() {
        val levels = mapOf(week[0] to 4, week[1] to 1, week[2] to 4, week[3] to 2)
        assertEquals("Read, last 7 days: done on 2 of 7 days", StatsGridMath.habitSummary("Read", week, levels))
    }
}
