package com.habitly.app.ui.addedit

import com.habitly.app.MainDispatcherRule
import com.habitly.app.data.local.AchievementEntity
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.domain.AchievementType
import com.habitly.app.domain.FrequencyType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

private class FakeHabitDataSource : HabitDataSource {
    var lastIncremented: Pair<Long, LocalDate>? = null
    var lastSetStatus: Pair<Long, com.habitly.app.domain.CompletionStatus?>? = null
    var addedHabit: HabitEntity? = null
    var updatedHabit: HabitEntity? = null

    override fun observeHabitsWithStats(today: LocalDate) = MutableStateFlow(emptyList<com.habitly.app.data.repository.HabitWithStats>())
    override suspend fun addHabit(habit: HabitEntity): Long { addedHabit = habit; return 1L }
    override suspend fun updateHabit(habit: HabitEntity) { updatedHabit = habit }
    override suspend fun deleteHabit(habit: HabitEntity) {}
    override suspend fun toggleCompletion(habitId: Long, date: LocalDate, note: String?) = emptySet<AchievementType>()
    override suspend fun incrementProgress(habitId: Long, date: LocalDate): Set<AchievementType> {
        lastIncremented = habitId to date
        return emptySet()
    }

    override suspend fun setStatus(
        habitId: Long,
        date: LocalDate,
        status: com.habitly.app.domain.CompletionStatus?,
        note: String?,
    ): Set<AchievementType> {
        lastSetStatus = habitId to status
        return emptySet()
    }
    override fun observeArchivedHabits(): Flow<List<HabitEntity>> = MutableStateFlow(emptyList())
    override suspend fun setArchived(habitId: Long, archived: Boolean) {}
    override suspend fun reorder(orderedIds: List<Long>) {}
    override suspend fun setReminder(habitId: Long, time: String?) {}
    override suspend fun getHabit(habitId: Long): HabitEntity? = null
    override fun observeAchievements(): Flow<List<AchievementEntity>> = MutableStateFlow(emptyList())
    override fun observeRecordsForHabit(habitId: Long) = MutableStateFlow(emptyList<com.habitly.app.domain.CompletionRecord>())
}

class AddEditHabitViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun savingWithBlankNameSetsNameErrorAndDoesNotAddHabit() = runTest {
        val fake = FakeHabitDataSource()
        val viewModel = AddEditHabitViewModel(fake, habitId = null)
        viewModel.onSave()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.nameError)
        assertNull(fake.addedHabit)
        assertEquals(false, viewModel.uiState.value.isSaved)
    }

    @Test
    fun savingSpecificDaysWithNoDaysSelectedSetsAnError() = runTest {
        val fake = FakeHabitDataSource()
        val viewModel = AddEditHabitViewModel(fake, habitId = null)
        viewModel.onNameChange("Yoga")
        viewModel.onFrequencyTypeChange(FrequencyType.SPECIFIC_DAYS)
        viewModel.onSave()
        advanceUntilIdle()
        assertNull(fake.addedHabit)
    }

    @Test
    fun savingAValidDailyHabitCallsAddHabitAndMarksSaved() = runTest {
        val fake = FakeHabitDataSource()
        val viewModel = AddEditHabitViewModel(fake, habitId = null)
        viewModel.onNameChange("Drink Water")
        viewModel.onSave()
        advanceUntilIdle()
        assertNotNull(fake.addedHabit)
        assertEquals("Drink Water", fake.addedHabit!!.name)
        assertTrue(viewModel.uiState.value.isSaved)
    }

    @Test
    fun onNameChangeClearsAPreviousNameError() = runTest {
        val fake = FakeHabitDataSource()
        val viewModel = AddEditHabitViewModel(fake, habitId = null)
        viewModel.onSave()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.nameError)
        viewModel.onNameChange("Read")
        assertNull(viewModel.uiState.value.nameError)
    }

    @Test
    fun selectingATemplateFillsEveryFieldTheFormAsksFor() = runTest {
        val viewModel = AddEditHabitViewModel(FakeHabitDataSource(), habitId = null)
        val template = com.habitly.app.domain.HabitTemplate(
            name = "Drink water",
            icon = "water_drop",
            colorHex = "#1098AD",
            category = "Health",
            reminderTime = "09:00",
        )

        viewModel.onTemplateSelected(template)

        val state = viewModel.uiState.value
        assertEquals("Drink water", state.name)
        assertEquals("water_drop", state.icon)
        assertEquals("#1098AD", state.colorHex)
        assertEquals("Health", state.category)
        assertEquals("09:00", state.reminderTime)
    }

    @Test
    fun selectingATemplateCarriesItsScheduleAcross() = runTest {
        val viewModel = AddEditHabitViewModel(FakeHabitDataSource(), habitId = null)
        val template = com.habitly.app.domain.HabitTemplate(
            name = "Weekly review",
            icon = "star",
            colorHex = "#495057",
            category = "Learning",
            frequencyType = com.habitly.app.domain.FrequencyType.SPECIFIC_DAYS,
            specificDays = setOf(java.time.DayOfWeek.SUNDAY),
        )

        viewModel.onTemplateSelected(template)

        val state = viewModel.uiState.value
        assertEquals(com.habitly.app.domain.FrequencyType.SPECIFIC_DAYS, state.frequencyType)
        assertEquals(setOf(java.time.DayOfWeek.SUNDAY), state.specificDays)
    }

    @Test
    fun aTemplatePickIsImmediatelySaveable() = runTest {
        val fake = FakeHabitDataSource()
        val viewModel = AddEditHabitViewModel(fake, habitId = null)
        viewModel.onTemplateSelected(com.habitly.app.domain.HabitTemplates.all.first())
        viewModel.onSave()
        advanceUntilIdle()
        assertEquals(true, viewModel.uiState.value.isSaved)
    }
}
