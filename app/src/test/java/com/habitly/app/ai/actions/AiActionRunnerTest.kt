package com.habitly.app.ai.actions

import com.habitly.app.data.local.AchievementEntity
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.local.HabitLogEntity
import com.habitly.app.data.repository.AchievementProgress
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.data.repository.HabitWithStats
import com.habitly.app.domain.AchievementType
import com.habitly.app.domain.CompletionRecord
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.FrequencyType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

private class RecordingData(var habit: HabitEntity?, val statuses: MutableMap<LocalDate, CompletionStatus> = mutableMapOf()) :
    HabitDataSource {
    val calls = mutableListOf<String>()
    var fail = false
    var cancel = false
    /** Extra fields per day (progress, note, time), as a real log row would carry them. */
    val rows = mutableMapOf<LocalDate, HabitLogEntity>()
    val restored = mutableListOf<HabitLogEntity?>()

    override suspend fun restoreLog(habitId: Long, date: LocalDate, previous: HabitLogEntity?) {
        restored += previous
        calls += "restore:$habitId:$date:${previous?.status}:${previous?.progress}"
        if (previous == null) statuses.remove(date) else statuses[date] = previous.status
    }

    private fun check() {
        if (cancel) throw kotlinx.coroutines.CancellationException("left the screen")
        if (fail) error("db down")
    }

    override suspend fun getHabit(habitId: Long): HabitEntity? = habit
    override suspend fun setReminder(habitId: Long, time: String?) { check(); calls += "reminder:$habitId:$time" }
    override suspend fun updateHabit(habit: HabitEntity) { check(); calls += "update:$habit"; this.habit = habit }
    override suspend fun setStatus(habitId: Long, date: LocalDate, status: CompletionStatus?, note: String?): Set<AchievementType> {
        check()
        calls += "status:$habitId:$date:$status"
        if (status == null) statuses.remove(date) else statuses[date] = status
        return emptySet()
    }
    override fun observeAllLogs(): Flow<List<HabitLogEntity>> = flowOf(
        statuses.map { (d, s) ->
            rows[d]?.copy(status = s) ?: HabitLogEntity(habitId = habit?.id ?: 0, date = d.toString(), status = s, completedAt = null, note = null)
        },
    )

    override fun observeHabitsWithStats(today: LocalDate): Flow<List<HabitWithStats>> = flowOf(emptyList())
    override fun observeHabitWithStats(habitId: Long, today: LocalDate): Flow<HabitWithStats?> = flowOf(null)
    override suspend fun addHabit(habit: HabitEntity): Long = 0L
    override suspend fun deleteHabit(habit: HabitEntity) {}
    override suspend fun toggleCompletion(habitId: Long, date: LocalDate, note: String?): Set<AchievementType> = emptySet()
    override suspend fun incrementProgress(habitId: Long, date: LocalDate): Set<AchievementType> = emptySet()
    override fun observeArchivedHabits(): Flow<List<HabitEntity>> = flowOf(emptyList())
    override suspend fun setArchived(habitId: Long, archived: Boolean) {}
    override suspend fun reorder(orderedIds: List<Long>) {}
    override fun observeAchievements(): Flow<List<AchievementEntity>> = flowOf(emptyList())
    override fun observeAchievementProgress(today: LocalDate): Flow<List<AchievementProgress>> = flowOf(emptyList())
    override suspend fun reconcileAchievements(): Set<AchievementType> = emptySet()
    override suspend fun resetAll() {}
    override fun observeRecordsForHabit(habitId: Long): Flow<List<CompletionRecord>> = flowOf(emptyList())
    override fun observeAllHabits(): Flow<List<HabitEntity>> = flowOf(listOfNotNull(habit))
}

class AiActionRunnerTest {
    private val today = LocalDate.of(2026, 10, 2) // a Friday
    private val original = HabitEntity(
        id = 7, name = "Run", icon = "run", colorHex = "#FFFFFF", category = "Health",
        frequencyType = FrequencyType.INTERVAL, frequencyDays = null, intervalDays = 2, reminderTime = "07:00",
        createdAt = 0L, isArchived = false, targetCount = 3,
    )

    private fun fake(habit: HabitEntity? = original) = RecordingData(habit)

    private suspend fun applied(runner: AiActionRunner, action: ValidAction): ActionOutcome.Applied {
        val outcome = runner.run(action, today)
        assertTrue("expected Applied but was $outcome", outcome is ActionOutcome.Applied)
        return outcome as ActionOutcome.Applied
    }

    @Test fun setReminderWritesAndUndoRestoresPrevious() = runTest {
        val data = fake()
        val out = applied(AiActionRunner(data), ValidAction.SetReminder(7, "x", "19:00"))
        assertEquals("Reminder moved to 19:00", out.message)
        assertEquals(listOf("reminder:7:19:00"), data.calls)
        out.undo()
        assertEquals("reminder:7:07:00", data.calls.last())
    }

    @Test fun setReminderUndoCanRestoreNull() = runTest {
        val data = fake(original.copy(reminderTime = null))
        val out = applied(AiActionRunner(data), ValidAction.SetReminder(7, "x", "19:00"))
        out.undo()
        assertEquals("reminder:7:null", data.calls.last())
    }

    @Test fun setDaysSortsByWeekdayAndUndoRestoresOriginal() = runTest {
        val data = fake()
        val out = applied(AiActionRunner(data), ValidAction.SetDays(7, "x", setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY)))
        assertEquals("Now on Mon, Fri", out.message)
        assertEquals(
            original.copy(frequencyType = FrequencyType.SPECIFIC_DAYS, frequencyDays = "MONDAY,FRIDAY", intervalDays = null),
            data.habit,
        )
        out.undo()
        assertEquals(original, data.habit)
    }

    @Test fun setGoalWritesAndUndoRestores() = runTest {
        val data = fake()
        val out = applied(AiActionRunner(data), ValidAction.SetGoal(7, "x", 5))
        assertEquals("Goal set to 5", out.message)
        assertEquals(5, data.habit?.targetCount)
        out.undo()
        assertEquals(original, data.habit)
    }

    @Test fun setRestDayMarksSkippedAndUndoDeletesWhenNothingWasLogged() = runTest {
        val data = fake()
        val date = LocalDate.of(2026, 10, 3)
        val out = applied(AiActionRunner(data), ValidAction.SetRestDay(7, "x", date))
        assertEquals("Rest day planned for Sat 3 Oct", out.message)
        assertEquals(CompletionStatus.SKIPPED, data.statuses[date])
        out.undo()
        assertEquals("restore:7:$date:null:null", data.calls.last())
        assertTrue(data.statuses.isEmpty())
    }

    @Test fun restTodayAndLogDoneRestorePreviousStatusOnUndo() = runTest {
        val data = fake().also { it.statuses[today] = CompletionStatus.MISSED }
        val runner = AiActionRunner(data)

        val rest = applied(runner, ValidAction.RestToday(7, "x"))
        assertEquals("Rest day for Run", rest.message)
        assertEquals(CompletionStatus.SKIPPED, data.statuses[today])
        rest.undo()
        assertEquals(CompletionStatus.MISSED, data.statuses[today])

        val done = applied(runner, ValidAction.LogDone(7, "x"))
        assertEquals("Logged Run", done.message)
        assertEquals(CompletionStatus.DONE, data.statuses[today])
        done.undo()
        assertEquals(CompletionStatus.MISSED, data.statuses[today])
    }

    @Test fun undoRestoresAGoalHabitsPartialCountNoteAndTime() = runTest {
        val data = fake().also {
            it.statuses[today] = CompletionStatus.PARTIAL
            it.rows[today] = HabitLogEntity(
                id = 3, habitId = 7, date = today.toString(), status = CompletionStatus.PARTIAL,
                completedAt = 123L, note = "half way", progress = 2,
            )
        }
        val out = applied(AiActionRunner(data), ValidAction.LogDone(7, "x"))
        out.undo()
        val previous = data.restored.single()!!
        assertEquals(CompletionStatus.PARTIAL, previous.status)
        assertEquals(2, previous.progress)
        assertEquals("half way", previous.note)
        assertEquals(123L, previous.completedAt)
    }

    @Test fun undoOnlyRevertsWhatTheActionChanged() = runTest {
        val data = fake()
        val runner = AiActionRunner(data)
        val days = applied(runner, ValidAction.SetDays(7, "x", setOf(DayOfWeek.MONDAY)))
        // The user renames the habit and moves its reminder before tapping Undo.
        data.habit = data.habit!!.copy(name = "Evening run", reminderTime = "19:30")
        days.undo()
        assertEquals(original.copy(name = "Evening run", reminderTime = "19:30"), data.habit)

        val goal = applied(runner, ValidAction.SetGoal(7, "x", 9))
        data.habit = data.habit!!.copy(colorHex = "#000000")
        goal.undo()
        assertEquals(original.targetCount, data.habit!!.targetCount)
        assertEquals("#000000", data.habit!!.colorHex)
    }

    @Test fun everyDayReadsAsDaily() = runTest {
        val out = applied(AiActionRunner(fake()), ValidAction.SetDays(7, "x", DayOfWeek.entries.toSet()))
        assertEquals("Now daily", out.message)
    }

    @Test fun cancellationIsNotSwallowed() = runTest {
        val data = fake().also { it.cancel = true }
        val thrown = runCatching { AiActionRunner(data).run(ValidAction.SetGoal(7, "x", 2), today) }.exceptionOrNull()
        assertTrue(thrown is kotlinx.coroutines.CancellationException)
    }

    @Test fun everyWritingActionFailsCleanly() = runTest {
        val actions = listOf(
            ValidAction.SetReminder(7, "x", "08:00"), ValidAction.SetDays(7, "x", setOf(DayOfWeek.MONDAY)),
            ValidAction.SetRestDay(7, "x", today), ValidAction.LogDone(7, "x"), ValidAction.RestToday(7, "x"),
        )
        for (action in actions) {
            val data = fake().also { it.fail = true }
            assertEquals(action.toString(), ActionOutcome.Failed, AiActionRunner(data).run(action, today))
        }
    }

    @Test fun openHabitNavigates() = runTest {
        assertEquals(ActionOutcome.Navigate(7), AiActionRunner(fake()).run(ValidAction.OpenHabit(7, "x"), today))
    }

    @Test fun missingHabitFails() = runTest {
        assertEquals(ActionOutcome.Failed, AiActionRunner(fake(null)).run(ValidAction.LogDone(7, "x"), today))
    }

    @Test fun repositoryErrorsBecomeFailed() = runTest {
        val data = fake().also { it.fail = true }
        assertEquals(ActionOutcome.Failed, AiActionRunner(data).run(ValidAction.SetGoal(7, "x", 2), today))
    }
}
