package com.habitly.app.ui.navigation

import com.habitly.app.domain.HabitTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationsTest {
    @Test fun `four tabs in bar order`() {
        assertEquals(listOf("today", "habits?segment={segment}", "insights?tab={tab}", "you"), TAB_DESTINATIONS.map { it.route })
    }
    @Test fun `pushed screens are not tabs`() {
        assertFalse(isTabRoute(Destination.Settings.route))
        assertFalse(isTabRoute(Destination.HabitDetail.route))
        assertTrue(isTabRoute(Destination.Today.route))
        assertTrue(isTabRoute(Destination.Habits.route))
        assertTrue(isTabRoute(Destination.Insights.route))
        assertFalse(isTabRoute("habits"))
    }
    @Test fun `add edit route encodes id and template`() {
        assertEquals("add_edit_habit?habitId=-1&template=drink_water", Destination.AddEditHabit.createRoute(template = "drink_water"))
        assertEquals("add_edit_habit?habitId=5&template=", Destination.AddEditHabit.createRoute(habitId = 5))
    }
    @Test fun `every template has a unique key that resolves back`() {
        val all = HabitTemplates.groups.flatMap { it.templates }
        assertEquals(all.size, all.map { it.key }.toSet().size)
        all.forEach { assertEquals(it, HabitTemplates.byKey(it.key)) }
        assertEquals("drink_water", HabitTemplates.byKey("drink_water")?.key)
        assertEquals("sleep_by_11pm", HabitTemplates.byKey("sleep_by_11pm")?.key)
    }
}
