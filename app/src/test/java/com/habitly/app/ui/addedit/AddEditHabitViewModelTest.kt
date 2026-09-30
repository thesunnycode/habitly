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
import org.junit.Assert.assertFalse
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
    var storedHabit: HabitEntity? = null

    val habitsWithStats = MutableStateFlow(emptyList<com.habitly.app.data.repository.HabitWithStats>())
    override fun observeHabitsWithStats(today: LocalDate) = habitsWithStats
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
    override suspend fun getHabit(habitId: Long): HabitEntity? = storedHabit?.takeIf { it.id == habitId }
    override fun observeAchievements(): Flow<List<AchievementEntity>> = MutableStateFlow(emptyList())
    override fun observeAchievementProgress(today: LocalDate): Flow<List<com.habitly.app.data.repository.AchievementProgress>> = MutableStateFlow(emptyList())
    override suspend fun reconcileAchievements() = emptySet<com.habitly.app.domain.AchievementType>()
    override fun observeHabitWithStats(habitId: Long, today: java.time.LocalDate): kotlinx.coroutines.flow.Flow<com.habitly.app.data.repository.HabitWithStats?> = kotlinx.coroutines.flow.flowOf(null)
    override suspend fun resetAll() {}
    override fun observeAllHabits(): kotlinx.coroutines.flow.Flow<List<com.habitly.app.data.local.HabitEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override fun observeAllLogs(): kotlinx.coroutines.flow.Flow<List<com.habitly.app.data.local.HabitLogEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
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
    private val fake = FakeHabitDataSource()

    @Test fun templateKeyPrefillsTheForm() = runTest {
        val vm = AddEditHabitViewModel(fake, habitId = null, templateKey = "drink_water"); advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals("Drink water", s.name)
        assertEquals("Health", s.category)
        assertEquals("09:00", s.reminderTime)
        assertEquals(8, s.targetCount)
    }

    @Test fun savingPersistsCategoryAndReminder() = runTest {
        val vm = AddEditHabitViewModel(fake, habitId = null); advanceUntilIdle()
        vm.onNameChange("Stretch"); vm.onCategoryChange("Fitness"); vm.onReminderTimeChange("07:15")
        vm.onSave(); advanceUntilIdle()
        val saved = fake.addedHabit!!
        assertEquals("Fitness", saved.category)
        assertEquals("07:15", saved.reminderTime)
    }

    @Test fun savedColoursAreAlwaysPaletteSeeds() = runTest {
        val vm = AddEditHabitViewModel(fake, habitId = null); advanceUntilIdle()
        vm.onNameChange("Stretch"); vm.onColorChange("#1098AD"); vm.onSave(); advanceUntilIdle()
        assertEquals("#22B8CF", fake.addedHabit!!.colorHex)
    }

    @Test fun isDirtyTracksEdits() = runTest {
        val vm = AddEditHabitViewModel(fake, habitId = null); advanceUntilIdle()
        assertFalse(vm.uiState.value.isDirty)
        vm.onNameChange("x")
        assertTrue(vm.uiState.value.isDirty)
    }

    @Test fun aTemplatePrefillIsNotDirtyUntilEdited() = runTest {
        val vm = AddEditHabitViewModel(fake, habitId = null, templateKey = "drink_water"); advanceUntilIdle()
        assertFalse(vm.uiState.value.isDirty)
        vm.onReminderTimeChange(null)
        assertTrue(vm.uiState.value.isDirty)
    }

    @Test fun editingLoadsTheHabitAndKeepsItsHistoryFields() = runTest {
        fake.storedHabit = HabitEntity(
            id = 7, name = "Read", icon = "menu_book", colorHex = "#9775FA", category = "Learning",
            frequencyType = FrequencyType.SPECIFIC_DAYS, frequencyDays = "MONDAY,FRIDAY", intervalDays = null,
            reminderTime = "21:00", createdAt = 1234L, isArchived = false, targetCount = 20, unit = "pages",
            sortOrder = 5,
        )
        val vm = AddEditHabitViewModel(fake, habitId = 7); advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals("Read", s.name)
        assertEquals(setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.FRIDAY), s.specificDays)
        assertEquals("21:00", s.reminderTime)
        assertFalse(s.isDirty)

        vm.onNameChange("Read more"); vm.onSave(); advanceUntilIdle()
        val updated = fake.updatedHabit!!
        assertEquals("Read more", updated.name)
        assertEquals(1234L, updated.createdAt)
        assertEquals(5, updated.sortOrder)
    }

    @Test fun existingCategoriesComeFromTheRepository() = runTest {
        fun habit(id: Long, category: String) = com.habitly.app.data.repository.HabitWithStats(
            habit = HabitEntity(
                id = id, name = "h$id", icon = "star", colorHex = "#FF922B", category = category,
                frequencyType = FrequencyType.DAILY, frequencyDays = null, intervalDays = null,
                reminderTime = null, createdAt = 0L, isArchived = false,
            ),
            currentStreak = 0, bestStreak = 0, completionRate = 0f, isCompletedToday = false,
        )
        fake.habitsWithStats.value = listOf(habit(1, "Chores"), habit(2, "Health"), habit(3, "Chores"))
        val vm = AddEditHabitViewModel(fake, habitId = null); advanceUntilIdle()
        assertEquals(listOf("Chores", "Health"), vm.uiState.value.existingCategories)
    }

    @Test fun editingAnUnknownHabitClosesAndNeverAddsOne() = runTest {
        val vm = AddEditHabitViewModel(fake, habitId = 42); advanceUntilIdle()
        assertTrue(vm.uiState.value.isNotFound)
        vm.onNameChange("Ghost"); vm.onSave(); advanceUntilIdle()
        assertNull(fake.addedHabit)
        assertNull(fake.updatedHabit)
    }
}
