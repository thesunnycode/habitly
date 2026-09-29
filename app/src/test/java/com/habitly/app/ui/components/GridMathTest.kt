package com.habitly.app.ui.components

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GridMathTest {
    @Test fun `week columns end with the week containing the end date`() {
        val cols = GridMath.weekColumns(LocalDate.of(2026, 9, 29), weeks = 3, weekStart = DayOfWeek.MONDAY)
        assertEquals(listOf(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 28)), cols)
    }
    @Test fun `week columns respect a Sunday week start`() {
        val cols = GridMath.weekColumns(LocalDate.of(2026, 9, 29), weeks = 1, weekStart = DayOfWeek.SUNDAY)
        assertEquals(listOf(LocalDate.of(2026, 9, 27)), cols)
    }
    @Test fun `month cells pad to whole weeks`() {
        val cells = GridMath.monthCells(YearMonth.of(2026, 9), DayOfWeek.MONDAY) // 1 Sep 2026 is a Tuesday
        assertNull(cells[0])
        assertEquals(LocalDate.of(2026, 9, 1), cells[1])
        assertEquals(0, cells.size % 7)
        assertEquals(30, cells.count { it != null })
    }
    @Test fun `month cells with Sunday start shift by one`() {
        val cells = GridMath.monthCells(YearMonth.of(2026, 9), DayOfWeek.SUNDAY)
        assertNull(cells[1])
        assertEquals(LocalDate.of(2026, 9, 1), cells[2])
    }
}
