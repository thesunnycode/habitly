package com.habitly.app.ui.create

import com.habitly.app.domain.HabitTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiDraftStoreTest {
    private val template = HabitTemplate("Wind down", "sleep", "#748FFC", "Health")

    @Test fun putThenTakeReturnsTheTemplateOnce() {
        val key = AiDraftStore.put(template)
        assertEquals(template, AiDraftStore.take(key))
        assertNull(AiDraftStore.take(key))
    }

    @Test fun keysAreUnique() {
        assertNotEquals(AiDraftStore.put(template), AiDraftStore.put(template))
    }

    @Test fun unknownKeyIsNull() {
        assertNull(AiDraftStore.take("nope"))
    }
}
