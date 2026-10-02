package com.habitly.app.ui.create

import com.habitly.app.ai.FrequencyDto
import com.habitly.app.ai.SuggestResponse
import com.habitly.app.ai.SuggestedHabitDto
import com.habitly.app.domain.FrequencyType
import com.habitly.app.ui.components.HABIT_ICON_KEYS
import com.habitly.app.ui.theme.HabitPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class SuggestionSanitizerTest {
    private val daily = FrequencyDto("DAILY")

    private fun dto(
        name: String = "Wind down",
        iconKey: String = "sleep",
        colorSeed: Int = 3,
        category: String = "Health",
        frequency: FrequencyDto = daily,
        targetCount: Int? = null,
        unit: String? = null,
        reminderTime: String? = null,
        why: String = "Helps you sleep",
    ) = SuggestedHabitDto(name, iconKey, colorSeed, category, frequency, targetCount, unit, reminderTime, why)

    private fun run(
        vararg habits: SuggestedHabitDto,
        intent: String = "sleep",
        existing: List<String> = emptyList(),
    ) = SuggestionSanitizer.sanitize(SuggestResponse(intent, habits.toList()), existing, HABIT_ICON_KEYS)

    private fun one(habit: SuggestedHabitDto, intent: String = "sleep") = run(habit, intent = intent).single().template

    @Test fun namesAreTrimmedCappedAndBlanksDropped() {
        assertEquals("Wind down", one(dto(name = "  Wind down  ")).name)
        assertEquals(40, one(dto(name = "x".repeat(60))).name.length)
        assertTrue(run(dto(name = "   "), dto(name = "")).isEmpty())
    }

    @Test fun duplicatesOfExistingOrEarlierSuggestionsAreDropped() {
        val out = run(dto(name = "Read"), dto(name = " read "), dto(name = "Stretch"), dto(name = "Walk"), existing = listOf("  WALK "))
        assertEquals(listOf("Read", "Stretch"), out.map { it.template.name })
    }

    @Test fun atMostFourAreReturned() {
        val out = run(*(1..7).map { dto(name = "Habit $it") }.toTypedArray())
        assertEquals(4, out.size)
    }

    @Test fun unknownIconFallsBackToIntentIcon() {
        assertEquals("sleep", one(dto(iconKey = "nope"), "sleep").icon)
        assertEquals("fitness_center", one(dto(iconKey = "nope"), "fitness").icon)
        assertEquals("phone_off", one(dto(iconKey = "nope"), "focus").icon)
        assertEquals("water_drop", one(dto(iconKey = "nope"), "health").icon)
        assertEquals("self_improvement", one(dto(iconKey = "nope"), "mind").icon)
        assertEquals("star", one(dto(iconKey = "nope"), "other").icon)
        assertEquals("star", one(dto(iconKey = "nope"), "whatever").icon)
        assertEquals("run", one(dto(iconKey = "run"), "sleep").icon)
    }

    @Test fun colourSeedIsClampedIntoThePalette() {
        val p = HabitPalette.seeds
        assertEquals(p[0], one(dto(colorSeed = -1)).colorHex)
        assertEquals(p.last(), one(dto(colorSeed = 99)).colorHex)
        assertEquals(p[3], one(dto(colorSeed = 3)).colorHex)
    }

    @Test fun unknownCategoryFallsBackToIntentCategory() {
        assertEquals("Health", one(dto(category = "Zzz"), "sleep").category)
        assertEquals("Fitness", one(dto(category = "Zzz"), "fitness").category)
        assertEquals("Productivity", one(dto(category = "Zzz"), "focus").category)
        assertEquals("Health", one(dto(category = "Zzz"), "health").category)
        assertEquals("Mind", one(dto(category = "Zzz"), "mind").category)
        assertEquals("General", one(dto(category = "Zzz"), "other").category)
        assertEquals("Learning", one(dto(category = "Learning"), "sleep").category)
    }

    @Test fun specificDaysAreParsedOrFallBackToDaily() {
        val ok = one(dto(frequency = FrequencyDto("SPECIFIC_DAYS", days = listOf("MONDAY", "FRIDAY"))))
        assertEquals(FrequencyType.SPECIFIC_DAYS, ok.frequencyType)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), ok.specificDays)
        for (days in listOf(emptyList(), listOf("FUNDAY"), null)) {
            val t = one(dto(frequency = FrequencyDto("SPECIFIC_DAYS", days = days)))
            assertEquals(FrequencyType.DAILY, t.frequencyType)
            assertTrue(t.specificDays.isEmpty())
        }
    }

    @Test fun intervalMustBeBetweenTwoAndThirty() {
        val three = one(dto(frequency = FrequencyDto("INTERVAL", intervalDays = 3)))
        assertEquals(FrequencyType.INTERVAL, three.frequencyType)
        assertEquals(3, three.intervalDays)
        for (step in listOf(1, 45, null)) {
            val t = one(dto(frequency = FrequencyDto("INTERVAL", intervalDays = step)))
            assertEquals(FrequencyType.DAILY, t.frequencyType)
            assertNull(t.intervalDays)
        }
    }

    @Test fun unknownFrequencyTypeIsDaily() {
        assertEquals(FrequencyType.DAILY, one(dto(frequency = FrequencyDto("MONTHLY"))).frequencyType)
    }

    @Test fun goalNeedsATargetAboveOne() {
        val ok = one(dto(targetCount = 8, unit = "cups"))
        assertEquals(8, ok.targetCount)
        assertEquals("cups", ok.unit)
        for (bad in listOf(1, 0, 1000)) {
            val t = one(dto(targetCount = bad, unit = "cups"))
            assertNull("$bad", t.targetCount)
            assertNull("$bad", t.unit)
        }
        assertEquals(20, one(dto(targetCount = 5, unit = "u".repeat(30))).unit!!.length)
        assertNull(one(dto(targetCount = null, unit = "cups")).unit)
        assertNull(one(dto(targetCount = 5, unit = "  ")).unit)
    }

    @Test fun reminderMustBeHhMm() {
        assertEquals("09:00", one(dto(reminderTime = "09:00")).reminderTime)
        assertNull(one(dto(reminderTime = "9am")).reminderTime)
        assertNull(one(dto(reminderTime = "24:00")).reminderTime)
        assertNull(one(dto(reminderTime = null)).reminderTime)
    }

    @Test fun whyIsTrimmedAndCapped() {
        assertEquals("Because", run(dto(why = "  Because  ")).single().why)
        assertEquals(90, run(dto(why = "w".repeat(200))).single().why.length)
    }
}
