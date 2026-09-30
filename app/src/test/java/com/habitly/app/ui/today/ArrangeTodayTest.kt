package com.habitly.app.ui.today

import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.repository.HabitWithStats
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.FrequencyType
import org.junit.Assert.assertEquals
import org.junit.Test

class ArrangeTodayTest {
    private fun habit(id: Long, status: CompletionStatus? = null) = HabitWithStats(
        habit = HabitEntity(id, "H$id", "star", "#FFFFFF", "Health", FrequencyType.DAILY, null, null, null, 0L, false),
        currentStreak = 0, bestStreak = 0, completionRate = 0f,
        isCompletedToday = status == CompletionStatus.DONE, todayStatus = status,
    )

    private fun ids(l: List<HabitWithStats>) = l.map { it.habit.id }

    @Test fun pinnedDoneHabitStaysInDueAtItsIndex() {
        val (due, done) = arrangeToday(listOf(habit(1), habit(3)), listOf(habit(2, CompletionStatus.DONE)), mapOf(2L to 1))
        assertEquals(listOf(1L, 2L, 3L), ids(due))
        assertEquals(emptyList<Long>(), ids(done))
        assertEquals(CompletionStatus.DONE, due[1].todayStatus)
    }

    @Test fun unpinnedDoneHabitsGoToDone() {
        val (due, done) = arrangeToday(
            listOf(habit(1)), listOf(habit(2, CompletionStatus.DONE), habit(4, CompletionStatus.DONE)), mapOf(2L to 0),
        )
        assertEquals(listOf(2L, 1L), ids(due))
        assertEquals(listOf(4L), ids(done))
    }

    @Test fun untickedPinnedHabitJustStaysInDue() {
        val (due, done) = arrangeToday(listOf(habit(1), habit(2)), emptyList(), mapOf(2L to 1))
        assertEquals(listOf(1L, 2L), ids(due))
        assertEquals(emptyList<Long>(), ids(done))
    }

    @Test fun pinningANotDoneHabitIsANoOp() {
        val (due, done) = arrangeToday(listOf(habit(1), habit(2)), listOf(habit(3, CompletionStatus.DONE)), mapOf(1L to 0))
        assertEquals(listOf(1L, 2L), ids(due))
        assertEquals(listOf(3L), ids(done))
    }

    @Test fun outOfRangeIndexIsClampedToTheEnd() {
        val (due, _) = arrangeToday(listOf(habit(1)), listOf(habit(2, CompletionStatus.DONE)), mapOf(2L to 9))
        assertEquals(listOf(1L, 2L), ids(due))
    }
}
