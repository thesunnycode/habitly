package com.habitly.app.ui.create

import com.habitly.app.domain.HabitTemplate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Hands an AI suggestion to the Add/Edit screen. A navigation route can only carry a short string,
 * so the template waits here under a key until the destination takes it (once).
 */
object AiDraftStore {
    private val drafts = ConcurrentHashMap<String, HabitTemplate>()
    private val counter = AtomicInteger()

    fun put(template: HabitTemplate): String = "d${counter.incrementAndGet()}".also { drafts[it] = template }

    fun take(key: String): HabitTemplate? = drafts.remove(key)
}
