package com.habitly.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class FrequencyValidatorTest {

    @Test
    fun `daily frequency is always valid`() {
        assertTrue(FrequencyValidator.isValid(FrequencyType.DAILY, emptySet(), null))
    }

    @Test
    fun `specific days requires at least one day selected`() {
        assertFalse(FrequencyValidator.isValid(FrequencyType.SPECIFIC_DAYS, emptySet(), null))
        assertTrue(
            FrequencyValidator.isValid(FrequencyType.SPECIFIC_DAYS, setOf(DayOfWeek.MONDAY), null)
        )
    }

    @Test
    fun `interval requires a positive interval day count`() {
        assertFalse(FrequencyValidator.isValid(FrequencyType.INTERVAL, emptySet(), null))
        assertFalse(FrequencyValidator.isValid(FrequencyType.INTERVAL, emptySet(), 0))
        assertTrue(FrequencyValidator.isValid(FrequencyType.INTERVAL, emptySet(), 3))
    }

    @Test
    fun `habit name must be non-blank and at most 40 chars`() {
        assertFalse(FrequencyValidator.validateName(""))
        assertFalse(FrequencyValidator.validateName("   "))
        assertTrue(FrequencyValidator.validateName("Drink Water"))
        assertFalse(FrequencyValidator.validateName("x".repeat(41)))
        assertTrue(FrequencyValidator.validateName("x".repeat(40)))
    }
}
