package com.habitly.app.ai

sealed interface AiResult<out T> {
    data class Success<T>(val value: T, val remaining: Int?) : AiResult<T>
    data object Offline : AiResult<Nothing>
    data class RateLimited(val resetAt: String?) : AiResult<Nothing>
    data class Failed(val reason: String) : AiResult<Nothing>
    /** No proxy URL/token in this build and Demo AI is off. */
    data object NotConfigured : AiResult<Nothing>
    /** The user has not accepted the AI consent sheet yet. */
    data object ConsentRequired : AiResult<Nothing>
    /** AI features are switched off in Settings. */
    data object Disabled : AiResult<Nothing>
}
