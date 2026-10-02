package com.habitly.app.ai.actions

import com.habitly.app.ai.AiActionDto
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.HabitSchedule
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** An AI-proposed action that has been checked against the user's current data and is safe to offer. */
sealed interface ValidAction {
    val habitId: Long
    val label: String

    data class SetReminder(override val habitId: Long, override val label: String, val time: String) : ValidAction
    data class SetDays(override val habitId: Long, override val label: String, val days: Set<DayOfWeek>) : ValidAction
    data class SetRestDay(override val habitId: Long, override val label: String, val date: LocalDate) : ValidAction
    data class SetGoal(override val habitId: Long, override val label: String, val target: Int) : ValidAction
    data class LogDone(override val habitId: Long, override val label: String) : ValidAction
    data class RestToday(override val habitId: Long, override val label: String) : ValidAction
    data class OpenHabit(override val habitId: Long, override val label: String) : ValidAction
}

/** Active habits and every logged status, as they were when the action was proposed. */
data class ValidationContext(
    val habits: Map<Long, HabitEntity>,
    val statuses: Map<Pair<Long, LocalDate>, CompletionStatus>,
)

object AiActionValidator {
    private const val MAX_LABEL = 28
    private const val MAX_REST_AHEAD_DAYS = 7L
    private val GOAL_RANGE = 1..999
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT)

    fun validate(
        action: AiActionDto,
        ctx: ValidationContext,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ValidAction? {
        val habit = ctx.habits[action.habitId]?.takeIf { !it.isArchived } ?: return null
        val id = habit.id
        fun label(default: String) = action.label.trim().take(MAX_LABEL).trim().ifEmpty { default }
        fun dueOn(date: LocalDate): Boolean {
            val created = Instant.ofEpochMilli(habit.createdAt).atZone(zone).toLocalDate()
            return HabitSchedule.parse(habit.frequencyType, habit.frequencyDays, habit.intervalDays, created).isDue(date)
        }
        fun openToday() = dueOn(today) && ctx.statuses[id to today] != CompletionStatus.DONE

        return when (action.type) {
            "set_reminder" -> {
                val time = action.time?.takeIf(::isValidTime) ?: return null
                ValidAction.SetReminder(id, label("Set reminder"), time)
            }
            "set_days" -> {
                val names = action.days?.takeIf { it.isNotEmpty() } ?: return null
                val days = names.map { runCatching { DayOfWeek.valueOf(it) }.getOrNull() ?: return null }.toSet()
                ValidAction.SetDays(id, label("Change days"), days)
            }
            "set_rest_day" -> {
                val date = action.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
                if (date.isBefore(today) || date.isAfter(today.plusDays(MAX_REST_AHEAD_DAYS)) || !dueOn(date)) return null
                ValidAction.SetRestDay(id, label("Plan a rest day"), date)
            }
            "set_goal" -> {
                val count = action.count?.takeIf { it in GOAL_RANGE } ?: return null
                if (habit.targetCount == null) return null
                ValidAction.SetGoal(id, label("Set goal"), count)
            }
            "log_done" -> if (openToday()) ValidAction.LogDone(id, label("Log it")) else null
            "rest_today" -> if (openToday()) ValidAction.RestToday(id, label("Rest today")) else null
            "open_habit" -> ValidAction.OpenHabit(id, label("Open habit"))
            else -> null
        }
    }

    suspend fun snapshot(data: HabitDataSource): ValidationContext = ValidationContext(
        habits = data.observeAllHabits().first().filter { !it.isArchived }.associateBy { it.id },
        statuses = data.observeAllLogs().first().associate { (it.habitId to LocalDate.parse(it.date)) to it.status },
    )

    private fun isValidTime(t: String): Boolean =
        t.length == 5 && runCatching { LocalTime.parse(t, timeFormat) }.isSuccess
}
