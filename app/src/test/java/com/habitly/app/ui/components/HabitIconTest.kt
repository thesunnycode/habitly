package com.habitly.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HabitIconTest {
    @Test fun `there are 36 icon keys and no duplicates`() {
        assertEquals(36, HABIT_ICON_KEYS.size)
        assertEquals(36, HABIT_ICON_KEYS.toSet().size)
    }
    @Test fun `every key except star maps to something other than the fallback`() {
        HABIT_ICON_KEYS.filter { it != "star" }.forEach { assertNotEquals(it, Icons.Rounded.Star, habitIconFor(it)) }
    }
    @Test fun `unknown keys fall back to star`() {
        assertEquals(Icons.Rounded.Star, habitIconFor("nope"))
    }
    @Test fun `template icon keys are all known`() {
        com.habitly.app.domain.HabitTemplates.groups.flatMap { it.templates }.forEach {
            assert(it.icon in HABIT_ICON_KEYS) { "template icon ${it.icon} missing" }
        }
    }
}
