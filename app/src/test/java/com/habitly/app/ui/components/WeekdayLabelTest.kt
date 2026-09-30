package com.habitly.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class WeekdayLabelTest {
    private val wednesday = LocalDate.of(2026, 9, 30)

    @Test fun `normal font scale shows the short name`() {
        assertEquals("Wed", weekdayLabel(wednesday, fontScale = 1f, locale = Locale.US))
    }

    @Test fun `large font scale drops to one letter so the chip stays on one line`() {
        assertEquals("W", weekdayLabel(wednesday, fontScale = 1.3f, locale = Locale.US))
        assertEquals("W", weekdayLabel(wednesday, fontScale = 2f, locale = Locale.US))
    }
}
