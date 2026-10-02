package com.habitly.app.ai.actions

import com.habitly.app.ai.AiActionDto
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.FrequencyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

class AiActionValidatorTest {
    // 2026-09-30 is a Wednesday.
    private val today = LocalDate.of(2026, 9, 30)
    private val created = today.minusDays(60).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun habit(
        id: Long = 1,
        type: FrequencyType = FrequencyType.DAILY,
        days: String? = null,
        target: Int? = null,
        archived: Boolean = false,
    ) = HabitEntity(
        id = id, name = "Habit $id", icon = "run", colorHex = "#FFFFFF", category = "Health",
        frequencyType = type, frequencyDays = days, intervalDays = null, reminderTime = null,
        createdAt = created, isArchived = archived, targetCount = target,
    )

    private fun ctx(
        vararg habits: HabitEntity,
        statuses: Map<Pair<Long, LocalDate>, CompletionStatus> = emptyMap(),
    ) = ValidationContext(habits.filter { !it.isArchived }.associateBy { it.id }, statuses)

    private fun validate(action: AiActionDto, ctx: ValidationContext = ctx(habit())) =
        AiActionValidator.validate(action, ctx, today, ZoneOffset.UTC)

    private fun dto(type: String, id: Long = 1, time: String? = null, days: List<String>? = null,
                    date: String? = null, count: Int? = null, label: String = "") =
        AiActionDto(type, id, time, days, date, count, label)

    @Test fun unknownOrArchivedHabitIsRejectedForEveryType() {
        val c = ctx(habit(id = 1), habit(id = 2, archived = true, target = 5))
        val types = listOf("set_reminder", "set_days", "set_rest_day", "set_goal", "log_done", "rest_today", "open_habit")
        for (type in types) for (id in listOf(2L, 99L)) {
            val action = dto(type, id, time = "19:00", days = listOf("MONDAY"), date = today.toString(), count = 3)
            assertNull("$type on $id", validate(action, c))
        }
    }

    @Test fun setReminderAcceptsOnlyHhMm() {
        assertEquals(
            ValidAction.SetReminder(1, "Set reminder", "19:00"),
            validate(dto("set_reminder", time = "19:00")),
        )
        for (bad in listOf("25:00", "7pm", "7:00", "19:60", "")) assertNull(bad, validate(dto("set_reminder", time = bad)))
        assertNull(validate(dto("set_reminder", time = null)))
    }

    @Test fun setDaysDeduplicatesAndRejectsBadInput() {
        val ok = validate(dto("set_days", days = listOf("FRIDAY", "MONDAY", "MONDAY")))
        assertEquals(ValidAction.SetDays(1, "Change days", setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)), ok)
        assertNull(validate(dto("set_days", days = emptyList())))
        assertNull(validate(dto("set_days", days = null)))
        assertNull(validate(dto("set_days", days = listOf("FUNDAY"))))
        assertNull(validate(dto("set_days", days = listOf("MONDAY", "FUNDAY"))))
    }

    @Test fun setRestDayWindowAndDueDay() {
        assertEquals(
            ValidAction.SetRestDay(1, "Plan a rest day", today),
            validate(dto("set_rest_day", date = today.toString())),
        )
        assertEquals(
            ValidAction.SetRestDay(1, "Plan a rest day", today.plusDays(7)),
            validate(dto("set_rest_day", date = today.plusDays(7).toString())),
        )
        for (bad in listOf(today.plusDays(8).toString(), today.minusDays(1).toString(), "2026-13-01", "soon")) {
            assertNull(bad, validate(dto("set_rest_day", date = bad)))
        }
        assertNull(validate(dto("set_rest_day", date = null)))

        // Mon/Fri habit: Wednesday today is not due, Friday is.
        val monFri = ctx(habit(type = FrequencyType.SPECIFIC_DAYS, days = "MONDAY,FRIDAY"))
        assertNull(validate(dto("set_rest_day", date = today.toString()), monFri))
        assertEquals(
            ValidAction.SetRestDay(1, "Plan a rest day", today.plusDays(2)),
            validate(dto("set_rest_day", date = today.plusDays(2).toString()), monFri),
        )
    }

    @Test fun setGoalNeedsAnExistingGoalAndAValidRange() {
        val goal = ctx(habit(target = 3))
        assertEquals(ValidAction.SetGoal(1, "Set goal", 1), validate(dto("set_goal", count = 1), goal))
        assertEquals(ValidAction.SetGoal(1, "Set goal", 999), validate(dto("set_goal", count = 999), goal))
        for (bad in listOf(0, 1000, -5)) assertNull("$bad", validate(dto("set_goal", count = bad), goal))
        assertNull(validate(dto("set_goal", count = null), goal))
        assertNull(validate(dto("set_goal", count = 5), ctx(habit(target = null))))
    }

    @Test fun logDoneAndRestTodayNeedADueUndoneDay() {
        assertEquals(ValidAction.LogDone(1, "Log it"), validate(dto("log_done")))
        assertEquals(ValidAction.RestToday(1, "Rest today"), validate(dto("rest_today")))

        val done = ctx(habit(), statuses = mapOf((1L to today) to CompletionStatus.DONE))
        assertNull(validate(dto("log_done"), done))
        assertNull(validate(dto("rest_today"), done))

        val skipped = ctx(habit(), statuses = mapOf((1L to today) to CompletionStatus.SKIPPED))
        assertEquals(ValidAction.LogDone(1, "Log it"), validate(dto("log_done"), skipped))

        val notDue = ctx(habit(type = FrequencyType.SPECIFIC_DAYS, days = "MONDAY,FRIDAY"))
        assertNull(validate(dto("log_done"), notDue))
        assertNull(validate(dto("rest_today"), notDue))
    }

    @Test fun openHabitIsValidForAnyActiveHabit() {
        val notDue = ctx(habit(type = FrequencyType.SPECIFIC_DAYS, days = "MONDAY"))
        assertEquals(ValidAction.OpenHabit(1, "Open habit"), validate(dto("open_habit"), notDue))
    }

    @Test fun unknownTypeIsRejected() {
        assertNull(validate(dto("delete_habit")))
        assertNull(validate(dto("")))
    }

    @Test fun labelsAreTrimmedCappedAndDefaulted() {
        assertEquals("Log it", validate(dto("log_done", label = "   "))?.label)
        assertEquals("Do it now", validate(dto("log_done", label = "  Do it now  "))?.label)
        val long = "x".repeat(40)
        assertEquals(28, validate(dto("log_done", label = long))?.label?.length)
        val defaults = mapOf(
            "set_reminder" to "Set reminder", "set_days" to "Change days", "set_rest_day" to "Plan a rest day",
            "set_goal" to "Set goal", "log_done" to "Log it", "rest_today" to "Rest today", "open_habit" to "Open habit",
        )
        val c = ctx(habit(target = 2))
        for ((type, label) in defaults) {
            val action = dto(type, time = "08:00", days = listOf("MONDAY"), date = today.toString(), count = 2)
            assertEquals(type, label, validate(action, c)?.label)
        }
    }
}
