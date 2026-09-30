package com.habitly.app.ai.ui

import com.habitly.app.ai.AiResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiStatesTest {

    @Test
    fun messagesMatchSpecCopy() {
        assertEquals("AI needs an internet connection", aiErrorMessage(AiResult.Offline))
        assertEquals("Daily AI limit reached — back tomorrow", aiErrorMessage(AiResult.RateLimited(null)))
        assertEquals("AI isn't set up in this build — turn on Demo AI in Settings", aiErrorMessage(AiResult.NotConfigured))
        assertEquals("AI features are turned off in Settings", aiErrorMessage(AiResult.Disabled))
        assertEquals("Something went wrong. Try again.", aiErrorMessage(AiResult.Failed("server")))
        assertNull(aiErrorMessage(AiResult.ConsentRequired))
        assertNull(aiErrorMessage(AiResult.Success(Unit, null)))
    }
}
