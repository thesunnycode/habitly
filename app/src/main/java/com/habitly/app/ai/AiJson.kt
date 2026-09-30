package com.habitly.app.ai

import kotlinx.serialization.json.Json

val AiJson: Json = Json { ignoreUnknownKeys = true; explicitNulls = true; encodeDefaults = true }
