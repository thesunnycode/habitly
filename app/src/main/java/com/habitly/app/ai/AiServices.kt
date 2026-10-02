package com.habitly.app.ai

import android.content.Context
import com.habitly.app.BuildConfig
import com.habitly.app.ai.openai.OpenAiApi

object AiServices {
    @Volatile private var prefs: AiPrefs? = null
    @Volatile private var gateway: AiGateway? = null

    fun prefs(context: Context): AiPrefs =
        prefs ?: synchronized(this) { prefs ?: AiPrefs(context.applicationContext).also { prefs = it } }

    fun gateway(context: Context): AiGateway = gateway ?: synchronized(this) {
        gateway ?: run {
            val p = prefs(context)
            // The key comes from local.properties at build time; without it only Demo AI can answer.
            val key = BuildConfig.AI_OPENAI_KEY
            val openAi = if (key.isBlank()) null else OpenAiApi(key, BuildConfig.AI_OPENAI_MODEL, quota = { p.consumeDailyAi() })
            AiGateway(p, openAi, DemoAiApi())
        }.also { gateway = it }
    }
}
