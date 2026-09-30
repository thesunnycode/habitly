package com.habitly.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiDtosTest {
    @Test fun encodesNullsExplicitly() {
        val zero = CountsDto(0, 0, 0, 0)
        val dto = HabitSummaryDto(
            id = 1, name = "Read", category = "Mind", frequency = FrequencyDto("DAILY"),
            createdDaysAgo = 3, currentStreak = 1, bestStreak = 2, last7 = zero, prev7 = zero,
            weekdays = listOf(WeekdayStatDto("MONDAY", 1, 1)), dueToday = true,
        )
        val text = AiJson.encodeToString(HabitSummaryDto.serializer(), dto)
        assertTrue(text, text.contains("\"unit\":null"))
        assertTrue(text, text.contains("\"weekdays\":["))
    }

    @Test fun decodesReviewPageWithoutOptionalFields() {
        val r = AiJson.decodeFromString(
            ReviewResponse.serializer(),
            """{"pages":[{"kind":"tip","headline":"H","body":"B"}]}""",
        )
        assertEquals(1, r.pages.size)
        assertNull(r.pages[0].habitId)
        assertNull(r.pages[0].action)
    }
}
