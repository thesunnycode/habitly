package com.habitly.app.domain

import com.habitly.app.ui.components.HABIT_COLOR_SWATCHES
import com.habitly.app.ui.components.HABIT_ICON_KEYS
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitTemplatesTest {

    @Test
    fun `every template uses an icon the picker can actually render`() {
        val unknown = HabitTemplates.all.filterNot { it.icon in HABIT_ICON_KEYS }
        assertTrue("Unknown icons: ${unknown.map { it.name to it.icon }}", unknown.isEmpty())
    }

    @Test
    fun `every template uses a colour from the app palette`() {
        val palette = HABIT_COLOR_SWATCHES.map { it.uppercase() }
        val offPalette = HabitTemplates.all.filterNot { it.colorHex.uppercase() in palette }
        assertTrue("Off-palette: ${offPalette.map { it.name to it.colorHex }}", offPalette.isEmpty())
    }

    @Test
    fun `template names are unique so the gallery has no duplicates`() {
        val names = HabitTemplates.all.map { it.name.lowercase() }
        assertTrue(names.size == names.distinct().size)
    }

    @Test
    fun `every group offers something to pick`() {
        assertTrue(HabitTemplates.groups.isNotEmpty())
        assertTrue(HabitTemplates.groups.all { it.templates.isNotEmpty() })
    }

    @Test
    fun `reminder times are stored as parseable 24 hour values`() {
        val bad = HabitTemplates.all.mapNotNull { it.reminderTime }.filterNot { time ->
            runCatching { java.time.LocalTime.parse(time) }.isSuccess
        }
        assertTrue("Unparseable: $bad", bad.isEmpty())
    }

    @Test
    fun `a specific-days template names its days, or it could never be saved`() {
        val incomplete = HabitTemplates.all.filter {
            it.frequencyType == FrequencyType.SPECIFIC_DAYS && it.specificDays.isEmpty()
        }
        assertTrue("Missing days: ${incomplete.map { it.name }}", incomplete.isEmpty())
    }

    @Test
    fun `every template passes the same validation the form applies`() {
        val invalid = HabitTemplates.all.filterNot { template ->
            FrequencyValidator.validateName(template.name) &&
                FrequencyValidator.isValid(template.frequencyType, template.specificDays, null)
        }
        assertTrue("Invalid: ${invalid.map { it.name }}", invalid.isEmpty())
    }
}
