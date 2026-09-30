package com.habitly.app.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

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

    @Test fun `each slice is one active habit's share of the cell, bottom slice first`() {
        // 4 habits, 10px cell, 2px gaps: each band is (10 - 2 * 3) / 4 = 1px.
        assertEquals(listOf(9f to 10f), StatsGridMath.sliceBands(count = 1, total = 4, height = 10f, gap = 2f))
        val full = StatsGridMath.sliceBands(count = 4, total = 4, height = 10f, gap = 2f)
        assertEquals(listOf(9f to 10f, 6f to 7f, 3f to 4f, 0f to 1f), full)
        assertEquals(listOf(5.5f to 10f, 0f to 4.5f), StatsGridMath.sliceBands(count = 2, total = 2, height = 10f, gap = 1f))
    }

    @Test fun `slice bands clamp to the slot count and handle nothing`() {
        assertEquals(2, StatsGridMath.sliceBands(count = 5, total = 2, height = 10f, gap = 1f).size)
        assertEquals(emptyList<Pair<Float, Float>>(), StatsGridMath.sliceBands(count = 0, total = 4, height = 10f, gap = 1f))
        assertEquals(emptyList<Pair<Float, Float>>(), StatsGridMath.sliceBands(count = 1, total = 0, height = 10f, gap = 1f))
    }

    @Test fun `a week cell describes what was done and rested that day`() {
        val day = LocalDate.of(2026, 9, 24)
        val slices = listOf(
            DaySlice("#4DABF7", rested = false, name = "Water"),
            DaySlice("#9775FA", rested = false, name = "Read"),
            DaySlice("#FF922B", rested = true, name = "Run"),
        )
        assertEquals("24 September: Water, Read done; Run rested", StatsGridMath.allDayDescription(day, slices, Locale.US))
        assertEquals("24 September: Run rested", StatsGridMath.allDayDescription(day, slices.takeLast(1), Locale.US))
        assertEquals("24 September: nothing done", StatsGridMath.allDayDescription(day, emptyList(), Locale.US))
    }

    @Test fun `a week cell in the habit view names its level`() {
        val day = LocalDate.of(2026, 9, 24)
        assertEquals("24 September: done", StatsGridMath.habitDayDescription(day, 4, beforeStart = false, Locale.US))
        assertEquals("24 September: partly done", StatsGridMath.habitDayDescription(day, 2, beforeStart = false, Locale.US))
        assertEquals("24 September: rested", StatsGridMath.habitDayDescription(day, 1, beforeStart = false, Locale.US))
        assertEquals("24 September: not done", StatsGridMath.habitDayDescription(day, 0, beforeStart = false, Locale.US))
        assertEquals("24 September: before this habit started", StatsGridMath.habitDayDescription(day, 0, beforeStart = true, Locale.US))
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
