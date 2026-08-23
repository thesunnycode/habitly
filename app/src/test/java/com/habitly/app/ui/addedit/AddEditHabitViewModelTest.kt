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
    var addedHabit: HabitEntity? = null
    var updatedHabit: HabitEntity? = null

    override fun observeHabitsWithStats(today: LocalDate) = MutableStateFlow(emptyList<com.habitly.app.data.repository.HabitWithStats>())
    override suspend fun addHabit(habit: HabitEntity): Long { addedHabit = habit; return 1L }
    override suspend fun updateHabit(habit: HabitEntity) { updatedHabit = habit }
    override suspend fun deleteHabit(habit: HabitEntity) {}
    override suspend fun toggleCompletion(habitId: Long, date: LocalDate, note: String?) = emptySet<AchievementType>()
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
}
