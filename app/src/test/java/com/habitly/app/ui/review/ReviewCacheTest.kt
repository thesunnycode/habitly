package com.habitly.app.ui.review

import com.habitly.app.ai.AiActionDto
import com.habitly.app.ai.ReviewPageDto
import com.habitly.app.ai.ReviewResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewCacheTest {
    private fun review(tag: String) = ReviewResponse(
        listOf(ReviewPageDto("summary", tag, "body", 3L, AiActionDto("log_done", 3L, label = "Do it"))),
    )

    @Test fun nullBlankAndGarbageDecodeToEmpty() {
        assertTrue(ReviewCache.decode(null).isEmpty())
        assertTrue(ReviewCache.decode("").isEmpty())
        assertTrue(ReviewCache.decode("{not json").isEmpty())
        assertTrue(ReviewCache.decode("[1,2]").isEmpty())
    }

    @Test fun roundTrips() {
        val map = mapOf("2026-09-28" to review("a"), "2026-09-21" to review("b"))
        assertEquals(map, ReviewCache.decode(ReviewCache.encode(map)))
    }

    @Test fun putKeepsTheFourMostRecentWeeks() {
        var map = emptyMap<String, ReviewResponse>()
        for (week in listOf("2026-09-07", "2026-09-28", "2026-09-14", "2026-09-21", "2026-10-05")) {
            map = ReviewCache.put(map, week, review(week))
        }
        assertEquals(listOf("2026-09-14", "2026-09-21", "2026-09-28", "2026-10-05"), map.keys.toList())
    }

    @Test fun putReplacesAnExistingWeek() {
        val first = ReviewCache.put(emptyMap(), "2026-09-28", review("old"))
        val second = ReviewCache.put(first, "2026-09-28", review("new"))
        assertEquals(1, second.size)
        assertEquals("new", second.getValue("2026-09-28").pages.single().headline)
    }
}
