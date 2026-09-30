package com.habitly.app.ai

interface AiApi {
    suspend fun suggestHabits(request: SuggestRequest): AiResult<SuggestResponse>
    suspend fun weeklyReview(request: ReviewRequest): AiResult<ReviewResponse>
    suspend fun coach(request: CoachRequest): AiResult<CoachResponse>
}
