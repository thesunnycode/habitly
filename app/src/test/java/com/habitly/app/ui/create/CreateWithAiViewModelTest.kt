package com.habitly.app.ui.create

import com.habitly.app.MainDispatcherRule
import com.habitly.app.ai.AiApi
import com.habitly.app.ai.AiGateway
import com.habitly.app.ai.AiResult
import com.habitly.app.ai.AiSettingsSource
import com.habitly.app.ai.CoachRequest
import com.habitly.app.ai.CoachResponse
import com.habitly.app.ai.FrequencyDto
import com.habitly.app.ai.ReviewRequest
import com.habitly.app.ai.ReviewResponse
import com.habitly.app.ai.SuggestRequest
import com.habitly.app.ai.SuggestResponse
import com.habitly.app.ai.SuggestedHabitDto
import com.habitly.app.data.local.AchievementEntity
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.local.HabitLogEntity
import com.habitly.app.data.repository.AchievementProgress
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.data.repository.HabitWithStats
import com.habitly.app.domain.AchievementType
import com.habitly.app.domain.CompletionRecord
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.DEFAULT_CATEGORIES
import com.habitly.app.domain.FrequencyType
import com.habitly.app.domain.toEntity
import com.habitly.app.ui.components.HABIT_ICON_KEYS
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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

private class FakeData(initial: List<HabitEntity> = emptyList()) : HabitDataSource {
    val habits = MutableStateFlow(initial)
    val added = mutableListOf<HabitEntity>()
    val deleted = mutableListOf<HabitEntity>()
    private var nextId = 100L

    override suspend fun addHabit(habit: HabitEntity): Long { added += habit; return nextId++ }
    override suspend fun deleteHabit(habit: HabitEntity) { deleted += habit }
    override fun observeAllHabits(): Flow<List<HabitEntity>> = habits

    override fun observeHabitsWithStats(today: LocalDate): Flow<List<HabitWithStats>> = flowOf(emptyList())
    override fun observeHabitWithStats(habitId: Long, today: LocalDate): Flow<HabitWithStats?> = flowOf(null)
    override suspend fun updateHabit(habit: HabitEntity) {}
    override suspend fun toggleCompletion(habitId: Long, date: LocalDate, note: String?): Set<AchievementType> = emptySet()
    override suspend fun incrementProgress(habitId: Long, date: LocalDate): Set<AchievementType> = emptySet()
    override suspend fun setStatus(habitId: Long, date: LocalDate, status: CompletionStatus?, note: String?): Set<AchievementType> = emptySet()
    override fun observeArchivedHabits(): Flow<List<HabitEntity>> = flowOf(emptyList())
    override suspend fun setArchived(habitId: Long, archived: Boolean) {}
    override suspend fun reorder(orderedIds: List<Long>) {}
    override suspend fun setReminder(habitId: Long, time: String?) {}
    override suspend fun getHabit(habitId: Long): HabitEntity? = null
    override fun observeAchievements(): Flow<List<AchievementEntity>> = flowOf(emptyList())
    override fun observeAchievementProgress(today: LocalDate): Flow<List<AchievementProgress>> = flowOf(emptyList())
    override suspend fun reconcileAchievements(): Set<AchievementType> = emptySet()
    override suspend fun resetAll() {}
    override fun observeRecordsForHabit(habitId: Long): Flow<List<CompletionRecord>> = flowOf(emptyList())
    override fun observeAllLogs(): Flow<List<HabitLogEntity>> = flowOf(emptyList())
}

private class Settings(consent: Boolean = true) : AiSettingsSource {
    override val aiEnabled = MutableStateFlow(true)
    override val aiConsent = MutableStateFlow(consent)
    override val demoAi = MutableStateFlow(false)
}

private class FakeApi : AiApi {
    val requests = mutableListOf<SuggestRequest>()
    var result: AiResult<SuggestResponse> = AiResult.Success(response(), remaining = 5)
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun suggestHabits(request: SuggestRequest): AiResult<SuggestResponse> {
        requests += request
        gate?.await()
        return result
    }
    override suspend fun weeklyReview(request: ReviewRequest): AiResult<ReviewResponse> = AiResult.Failed("unused")
    override suspend fun coach(request: CoachRequest): AiResult<CoachResponse> = AiResult.Failed("unused")
}

private fun suggestion(name: String) = SuggestedHabitDto(
    name = name, iconKey = "sleep", colorSeed = 1, category = "Health",
    frequency = FrequencyDto("DAILY"), reminderTime = "22:00", why = "Because",
)

private fun response(vararg names: String = arrayOf("Wind down", "No caffeine after 2pm")) =
    SuggestResponse("sleep", names.map(::suggestion))

class CreateWithAiViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val data = FakeData()
    private val api = FakeApi()
    private val settings = Settings()
    private val now = 1_790_000_000_000L
    private var consentGranted = 0

    private fun vm(settings: Settings = this.settings) = CreateWithAiViewModel(
        gateway = AiGateway(settings, api, api),
        data = data,
        allowConsent = { consentGranted++; settings.aiConsent.value = true },
        now = { now },
    )

    private fun CreateWithAiViewModel.suggest(text: String = "sleep earlier") {
        onTextChange(text)
        onSuggest()
    }

    @Test fun suggestDisabledUnderThreeChars() = runTest {
        val vm = vm()
        vm.onTextChange("hi")
        assertFalse(vm.uiState.value.canSuggest)
        vm.onTextChange("run")
        assertTrue(vm.uiState.value.canSuggest)
        vm.onTextChange("  ru ")
        assertFalse(vm.uiState.value.canSuggest)
    }

    @Test fun textIsCappedAtThreeHundredChars() = runTest {
        val vm = vm()
        vm.onTextChange("a".repeat(400))
        assertEquals(MAX_PROMPT_CHARS, vm.uiState.value.text.length)
    }

    @Test fun chipFillsText() = runTest {
        val vm = vm()
        vm.onChip("Sleep better")
        assertEquals("Sleep better", vm.uiState.value.text)
    }

    @Test fun successShowsSanitizedCards() = runTest {
        data.habits.value = listOf(
            habit("Meditate"),
            habit("Old", archived = true),
        )
        val vm = vm()
        vm.suggest("  sleep earlier  ")
        advanceUntilIdle()

        val req = api.requests.single()
        assertEquals("sleep earlier", req.text)
        assertEquals(listOf("Meditate"), req.existingHabitNames)
        assertEquals(HABIT_ICON_KEYS, req.iconKeys)
        assertEquals(DEFAULT_CATEGORIES, req.categories)

        val s = vm.uiState.value
        assertEquals("sleep", s.intent)
        assertEquals(2, s.cards.size)
        assertTrue(s.cards.all { it.status == CardStatus.NEW })
        assertFalse(s.isLoading)
        assertNull(s.error)
    }

    @Test fun loadingState() = runTest {
        api.gate = CompletableDeferred()
        val vm = vm()
        vm.suggest()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.cards.isEmpty())
        assertFalse(vm.uiState.value.canSuggest)

        vm.onSuggest() // ignored while loading
        api.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, api.requests.size)
        assertFalse(vm.uiState.value.isLoading)
        assertEquals(2, vm.uiState.value.cards.size)
    }

    @Test fun emptyAfterSanitizingIsAnEmptyMessageNotAnError() = runTest {
        data.habits.value = listOf(habit("Wind down"))
        api.result = AiResult.Success(response("Wind down", " wind DOWN "), remaining = 4)
        val vm = vm()
        vm.suggest()
        advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals("No new ideas this time — try describing it differently", s.emptyMessage)
        assertNull(s.error)
        assertTrue(s.cards.isEmpty())
    }

    @Test fun consentFlow() = runTest {
        val noConsent = Settings(consent = false)
        val vm = vm(noConsent)
        vm.suggest()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.needsConsent)
        assertEquals(0, api.requests.size)

        vm.onConsentAllowed()
        advanceUntilIdle()
        assertEquals(1, consentGranted)
        assertFalse(vm.uiState.value.needsConsent)
        assertEquals(1, api.requests.size)
        assertEquals(2, vm.uiState.value.cards.size)
    }

    @Test fun consentDismissedClosesTheSheet() = runTest {
        val vm = vm(Settings(consent = false))
        vm.suggest()
        advanceUntilIdle()
        vm.onConsentDismissed()
        assertFalse(vm.uiState.value.needsConsent)
        assertEquals(0, consentGranted)
    }

    @Test fun addAndUndo() = runTest {
        val vm = vm()
        vm.suggest()
        advanceUntilIdle()
        val card = vm.uiState.value.cards.first()

        vm.onAdd(card.key)
        advanceUntilIdle()
        val entity = data.added.single()
        assertEquals(card.suggestion.template.toEntity(now), entity)
        val added = vm.uiState.value.cards.first { it.key == card.key }
        assertEquals(CardStatus.ADDED, added.status)
        assertEquals(100L, added.habitId)

        vm.onUndo(card.key)
        advanceUntilIdle()
        assertEquals(entity.copy(id = 100L), data.deleted.single())
        val undone = vm.uiState.value.cards.first { it.key == card.key }
        assertEquals(CardStatus.NEW, undone.status)
        assertNull(undone.habitId)
    }

    @Test fun doubleTapAddOnlyAddsOnce() = runTest {
        val vm = vm()
        vm.suggest()
        advanceUntilIdle()
        val key = vm.uiState.value.cards.first().key
        vm.onAdd(key)
        vm.onAdd(key)
        advanceUntilIdle()
        assertEquals(1, data.added.size)
    }

    @Test fun addAllAddsOnlyNewCards() = runTest {
        api.result = AiResult.Success(response("One", "Two", "Three", "Four"), remaining = 5)
        val vm = vm()
        vm.suggest()
        advanceUntilIdle()
        val keys = vm.uiState.value.cards.map { it.key }
        vm.onAdd(keys[0])
        vm.onDismiss(keys[1])
        advanceUntilIdle()
        data.added.clear()

        vm.onAddAll()
        advanceUntilIdle()
        assertEquals(listOf("Three", "Four"), data.added.map { it.name })
        val statuses = vm.uiState.value.cards.associate { it.key to it.status }
        assertEquals(CardStatus.ADDED, statuses[keys[0]])
        assertEquals(CardStatus.DISMISSED, statuses[keys[1]])
        assertEquals(CardStatus.ADDED, statuses[keys[2]])
        assertEquals(CardStatus.ADDED, statuses[keys[3]])
        assertFalse(vm.uiState.value.hasNew)
    }

    @Test fun dismissHidesTheCard() = runTest {
        val vm = vm()
        vm.suggest()
        advanceUntilIdle()
        val key = vm.uiState.value.cards.first().key
        vm.onDismiss(key)
        val s = vm.uiState.value
        assertEquals(CardStatus.DISMISSED, s.cards.first { it.key == key }.status)
        assertEquals(1, s.visibleCards.size)
    }

    @Test fun editReturnsDraftKey() = runTest {
        val vm = vm()
        vm.suggest()
        advanceUntilIdle()
        val card = vm.uiState.value.cards.first()
        val draftKey = vm.onEdit(card.key)
        assertNotNull(draftKey)
        assertEquals(card.suggestion.template, AiDraftStore.take(draftKey!!))
        assertNull(vm.onEdit(12345))
    }

    @Test fun errorsSurfaceAndTryAgainResendsTheSameText() = runTest {
        api.result = AiResult.Offline
        val vm = vm()
        vm.suggest("  sleep earlier ")
        advanceUntilIdle()
        assertEquals(AiResult.Offline, vm.uiState.value.error)
        assertTrue(vm.uiState.value.cards.isEmpty())

        api.result = AiResult.Success(response(), remaining = 3)
        vm.onTextChange("something else typed after")
        vm.onTryAgain()
        advanceUntilIdle()
        assertEquals(listOf("sleep earlier", "sleep earlier"), api.requests.map { it.text })
        assertNull(vm.uiState.value.error)
        assertEquals(2, vm.uiState.value.cards.size)
    }

    @Test fun remainingFromGateway() = runTest {
        api.result = AiResult.Success(response(), remaining = 2)
        val vm = vm()
        vm.suggest()
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.remaining)
    }

    private fun habit(name: String, archived: Boolean = false) = HabitEntity(
        name = name, icon = "star", colorHex = "#FF922B", category = "Health",
        frequencyType = FrequencyType.DAILY, frequencyDays = null, intervalDays = null, reminderTime = null,
        createdAt = 0L, isArchived = archived,
    )
}
