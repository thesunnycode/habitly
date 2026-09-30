package com.habitly.app.ui.achievements

import org.junit.Assert.assertEquals
import org.junit.Test

class BadgeColumnsTest {
    @Test fun `two columns at normal and moderately large text`() {
        assertEquals(2, badgeColumns(1f))
        assertEquals(2, badgeColumns(1.3f))
    }

    @Test fun `one column from 150 percent text so titles wrap only between words`() {
        assertEquals(1, badgeColumns(1.5f))
        assertEquals(1, badgeColumns(2f))
    }
}
