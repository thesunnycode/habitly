package com.habitly.app.ai

import android.content.Context
import com.habitly.app.BuildConfig

object AiServices {
    @Volatile private var prefs: AiPrefs? = null
    @Volatile private var gateway: AiGateway? = null

    fun prefs(context: Context): AiPrefs =
        prefs ?: synchronized(this) { prefs ?: AiPrefs(context.applicationContext).also { prefs = it } }

    fun gateway(context: Context): AiGateway = gateway ?: synchronized(this) {
        gateway ?: run {
            val p = prefs(context)
            val http = if (BuildConfig.AI_BASE_URL.isBlank() || BuildConfig.AI_APP_TOKEN.isBlank()) null
            else HttpAiApi(BuildConfig.AI_BASE_URL, BuildConfig.AI_APP_TOKEN, deviceId = { p.installId() })
            AiGateway(p, http, DemoAiApi())
        }.also { gateway = it }
    }
}
