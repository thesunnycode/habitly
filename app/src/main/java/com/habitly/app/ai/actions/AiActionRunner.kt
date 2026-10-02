package com.habitly.app.ai.actions

import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.FrequencyType
import com.habitly.app.domain.HabitSchedule
import com.habitly.app.domain.label
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed interface ActionOutcome {
    /** The change went through; [undo] puts back what was there before. */
    data class Applied(val message: String, val undo: suspend () -> Unit) : ActionOutcome
    data class Navigate(val habitId: Long) : ActionOutcome
    data object Failed : ActionOutcome
}

/** Applies a [ValidAction] through the repository, handing back an Undo for every change it makes. */
class AiActionRunner(private val data: HabitDataSource) {
    private val restDayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

    suspend fun run(action: ValidAction, today: LocalDate): ActionOutcome = try {
        apply(action, today)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ActionOutcome.Failed
    }

    private suspend fun apply(action: ValidAction, today: LocalDate): ActionOutcome {
        if (action is ValidAction.OpenHabit) return ActionOutcome.Navigate(action.habitId)
        val habit = data.getHabit(action.habitId) ?: return ActionOutcome.Failed
        return when (action) {
            is ValidAction.SetReminder -> {
                data.setReminder(habit.id, action.time)
                applied("Reminder moved to ${action.time}") { data.setReminder(habit.id, habit.reminderTime) }
            }
            is ValidAction.SetDays -> {
                val sorted = action.days.sorted()
                data.updateHabit(
                    habit.copy(
                        frequencyType = FrequencyType.SPECIFIC_DAYS,
                        frequencyDays = sorted.joinToString(",") { it.name },
                        intervalDays = null,
                    ),
                )
                val label = HabitSchedule(FrequencyType.SPECIFIC_DAYS, action.days).label(Locale.ENGLISH)
                applied(if (label == "Daily") "Now daily" else "Now on $label") { data.updateHabit(habit) }
            }
            is ValidAction.SetGoal -> {
                data.updateHabit(habit.copy(targetCount = action.target))
                applied("Goal set to ${action.target}") { data.updateHabit(habit) }
            }
            is ValidAction.SetRestDay ->
                setStatus(habit, action.date, CompletionStatus.SKIPPED, "Rest day planned for ${action.date.format(restDayFormat)}")
            is ValidAction.RestToday ->
                setStatus(habit, today, CompletionStatus.SKIPPED, "Rest day for ${habit.name}")
            is ValidAction.LogDone ->
                setStatus(habit, today, CompletionStatus.DONE, "Logged ${habit.name}")
            is ValidAction.OpenHabit -> ActionOutcome.Navigate(habit.id)
        }
    }

    private suspend fun setStatus(
        habit: HabitEntity,
        date: LocalDate,
        status: CompletionStatus,
        message: String,
    ): ActionOutcome {
        val previous = data.observeAllLogs().first()
            .firstOrNull { it.habitId == habit.id && it.date == date.toString() }?.status
        data.setStatus(habit.id, date, status)
        return applied(message) { data.setStatus(habit.id, date, previous) }
    }

    private fun applied(message: String, undo: suspend () -> Unit) = ActionOutcome.Applied(message, undo)
}
