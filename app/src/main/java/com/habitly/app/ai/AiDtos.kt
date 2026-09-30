package com.habitly.app.ai

import kotlinx.serialization.Serializable

@Serializable data class FrequencyDto(val type: String, val days: List<String>? = null, val intervalDays: Int? = null)
@Serializable data class CountsDto(val done: Int, val rested: Int, val missed: Int, val due: Int)
@Serializable data class WeekdayStatDto(val day: String, val done: Int, val due: Int)

@Serializable data class HabitSummaryDto(
    val id: Long, val name: String, val category: String, val frequency: FrequencyDto,
    val targetCount: Int? = null, val unit: String? = null, val reminderTime: String? = null,
    val createdDaysAgo: Int, val currentStreak: Int, val bestStreak: Int,
    val last7: CountsDto, val prev7: CountsDto, val weekdays: List<WeekdayStatDto>,
    val avgCheckInHour: Int? = null, val dueToday: Boolean, val todayStatus: String? = null, val risk: Double? = null,
)

@Serializable data class AiActionDto(
    val type: String, val habitId: Long, val time: String? = null, val days: List<String>? = null,
    val date: String? = null, val count: Int? = null, val label: String = "",
)

@Serializable data class SuggestRequest(val text: String, val existingHabitNames: List<String>, val iconKeys: List<String>, val categories: List<String>)
@Serializable data class SuggestedHabitDto(
    val name: String, val iconKey: String, val colorSeed: Int, val category: String, val frequency: FrequencyDto,
    val targetCount: Int? = null, val unit: String? = null, val reminderTime: String? = null, val why: String = "",
)
@Serializable data class SuggestResponse(val intent: String, val habits: List<SuggestedHabitDto>)

@Serializable data class ReviewRequest(val weekStart: String, val habits: List<HabitSummaryDto>)
@Serializable data class ReviewPageDto(val kind: String, val headline: String, val body: String, val habitId: Long? = null, val action: AiActionDto? = null)
@Serializable data class ReviewResponse(val pages: List<ReviewPageDto>)

@Serializable data class ChatMessageDto(val role: String, val text: String)
@Serializable data class CoachRequest(val today: String, val habits: List<HabitSummaryDto>, val messages: List<ChatMessageDto>)
@Serializable data class CoachResponse(val reply: String, val actions: List<AiActionDto> = emptyList())
