package com.habitly.app.ui.coach

import com.habitly.app.MainDispatcherRule
import com.habitly.app.ai.AiActionDto
import com.habitly.app.ai.AiApi
import com.habitly.app.ai.AiContextSource
import com.habitly.app.ai.AiGateway
import com.habitly.app.ai.AiResult
import com.habitly.app.ai.AiSettingsSource
import com.habitly.app.ai.CoachRequest
import com.habitly.app.ai.CoachResponse
import com.habitly.app.ai.ReviewRequest
import com.habitly.app.ai.ReviewResponse
import com.habitly.app.ai.SuggestRequest
import com.habitly.app.ai.SuggestResponse
import com.habitly.app.ai.actions.AiActionRunner
import com.habitly.app.ai.actions.ValidAction
import com.habitly.app.ai.actions.ValidationContext
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

private val TODAY = LocalDate.of(2026, 10, 2)

private val HABIT = HabitEntity(
    id = 7, name = "Run", icon = "run", colorHex = "#FF922B", category = "Fitness",
    frequencyType = FrequencyType.DAILY, frequencyDays = null, intervalDays = null, reminderTime = null,
    createdAt = TODAY.minusDays(30).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), isArchived = false,
)

private class FakeData : HabitDataSource {
    val statusCalls = mutableListOf<Triple<Long, LocalDate, CompletionStatus?>>()
    var failWrites = false

    override suspend fun getHabit(habitId: Long): HabitEntity? = HABIT.takeIf { it.id == habitId }
    override suspend fun setStatus(habitId: Long, date: LocalDate, status: CompletionStatus?, note: String?): Set<AchievementType> {
        if (failWrites) error("db down")
        statusCalls += Triple(habitId, date, status)
        return emptySet()
    }
    override fun observeAllHabits(): Flow<List<HabitEntity>> = flowOf(listOf(HABIT))
    override fun observeAllLogs(): Flow<List<HabitLogEntity>> = flowOf(emptyList())

    override fun observeHabitsWithStats(today: LocalDate): Flow<List<HabitWithStats>> = flowOf(emptyList())
    override fun observeHabitWithStats(habitId: Long, today: LocalDate): Flow<HabitWithStats?> = flowOf(null)
    override suspend fun addHabit(habit: HabitEntity): Long = 0L
    override suspend fun updateHabit(habit: HabitEntity) {}
    override suspend fun deleteHabit(habit: HabitEntity) {}
    override suspend fun toggleCompletion(habitId: Long, date: LocalDate, note: String?): Set<AchievementType> = emptySet()
    override suspend fun incrementProgress(habitId: Long, date: LocalDate): Set<AchievementType> = emptySet()
    override fun observeArchivedHabits(): Flow<List<HabitEntity>> = flowOf(emptyList())
    override suspend fun setArchived(habitId: Long, archived: Boolean) {}
    override suspend fun reorder(orderedIds: List<Long>) {}
    override suspend fun setReminder(habitId: Long, time: String?) {}
    override fun observeAchievements(): Flow<List<AchievementEntity>> = flowOf(emptyList())
    override fun observeAchievementProgress(today: LocalDate): Flow<List<AchievementProgress>> = flowOf(emptyList())
    override suspend fun reconcileAchievements(): Set<AchievementType> = emptySet()
    override suspend fun resetAll() {}
    override fun observeRecordsForHabit(habitId: Long): Flow<List<CompletionRecord>> = flowOf(emptyList())
}

private class Settings(consent: Boolean = true) : AiSettingsSource {
    override val aiEnabled = MutableStateFlow(true)
    override val aiConsent = MutableStateFlow(consent)
    override val demoAi = MutableStateFlow(false)
}

private class FakeApi : AiApi {
    val requests = mutableListOf<CoachRequest>()
    var result: AiResult<CoachResponse> = AiResult.Success(CoachResponse("Hello"), remaining = 9)
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun coach(request: CoachRequest): AiResult<CoachResponse> {
        requests += request
        gate?.await()
        return result
    }
    override suspend fun suggestHabits(request: SuggestRequest): AiResult<SuggestResponse> = AiResult.Failed("unused")
    override suspend fun weeklyReview(request: ReviewRequest): AiResult<ReviewResponse> = AiResult.Failed("unused")
}

class CoachViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val data = FakeData()
    private val api = FakeApi()
    private var consentGranted = 0

    private fun vm(settings: Settings = Settings()) = CoachViewModel(
        gateway = AiGateway(settings, api, api),
        context = AiContextSource(data),
        validation = {
            ValidationContext(mapOf(7L to HABIT), emptyMap())
        },
        runner = AiActionRunner(data),
        allowConsent = { consentGranted++; settings.aiConsent.value = true },
        today = { TODAY },
    )

    private fun action(type: String, id: Long = 7, time: String? = null) = AiActionDto(type, id, time = time)

    @Test fun openRequestsOpeningOnce() = runTest {
        val vm = vm()
        vm.open()
        advanceUntilIdle()
        val req = api.requests.single()
        assertEquals("2026-10-02", req.today)
        assertTrue(req.messages.isEmpty())
        assertEquals(1, req.habits.size)
        val messages = vm.uiState.value.messages
        assertEquals(listOf(CoachRole.COACH), messages.map { it.role })
        assertEquals("Hello", messages.single().text)

        vm.open()
        advanceUntilIdle()
        assertEquals(1, api.requests.size)
        assertEquals(1, vm.uiState.value.messages.size)
    }

    @Test fun typingIndicatorWhileWaiting() = runTest {
        api.gate = CompletableDeferred()
        val vm = vm()
        vm.open()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isTyping)
        api.gate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isTyping)
    }

    @Test fun sendAppendsUserThenCoachAndTrimsHistoryToSix() = runTest {
        val vm = vm()
        vm.open()
        advanceUntilIdle()
        repeat(3) { i ->
            vm.onInputChange("question $i")
            vm.onSend()
            advanceUntilIdle()
        }
        // opening + 3 x (user, coach) = 7 messages so far
        vm.onInputChange("Why do I slip?")
        assertTrue(vm.uiState.value.canSend)
        vm.onSend()
        assertEquals("", vm.uiState.value.input)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(CoachRole.USER, state.messages[state.messages.size - 2].role)
        assertEquals("Why do I slip?", state.messages[state.messages.size - 2].text)
        assertEquals(CoachRole.COACH, state.messages.last().role)

        val sent = api.requests.last().messages
        assertEquals(6, sent.size)
        assertEquals("user", sent.last().role)
        assertEquals("Why do I slip?", sent.last().text)
        assertEquals(listOf("coach", "user", "coach", "user", "coach", "user"), sent.map { it.role })
    }

    @Test fun inputIsCappedAndBlankCannotSend() = runTest {
        val vm = vm()
        vm.onInputChange("a".repeat(400))
        assertEquals(300, vm.uiState.value.input.length)
        vm.onInputChange("   ")
        assertFalse(vm.uiState.value.canSend)
        vm.onSend()
        advanceUntilIdle()
        assertTrue(api.requests.isEmpty())
    }

    @Test fun chipSendsImmediately() = runTest {
        val vm = vm()
        vm.onChip("Plan my day")
        advanceUntilIdle()
        assertEquals("Plan my day", api.requests.single().messages.single().text)
        assertEquals(listOf(CoachRole.USER, CoachRole.COACH), vm.uiState.value.messages.map { it.role })
    }

    @Test fun actionsAreValidatedAndCappedAtTwo() = runTest {
        api.result = AiResult.Success(
            CoachResponse("Try this", listOf(action("log_done"), action("set_reminder", id = 99, time = "19:00"), action("open_habit"), action("rest_today"))),
            remaining = null,
        )
        val vm = vm()
        vm.open()
        advanceUntilIdle()
        val actions = vm.uiState.value.messages.single().actions
        assertEquals(listOf(ValidAction.LogDone(7, "Log it"), ValidAction.OpenHabit(7, "Open habit")), actions)
    }

    @Test fun onActionAppliesEmitsEventAndRemovesTheAction() = runTest {
        api.result = AiResult.Success(CoachResponse("Do it", listOf(action("log_done"))), remaining = null)
        val vm = vm()
        vm.open()
        advanceUntilIdle()
        val message = vm.uiState.value.messages.single()

        vm.onAction(message.id, message.actions.single())
        advanceUntilIdle()
        val event = vm.events.first()
        assertTrue(event is CoachEvent.Applied)
        assertEquals("Logged Run", (event as CoachEvent.Applied).message)
        assertEquals(listOf(Triple(7L, TODAY, CompletionStatus.DONE)), data.statusCalls)
        val after = vm.uiState.value.messages.single()
        assertTrue(after.actions.isEmpty())
        assertEquals("Logged Run", after.appliedNote)
    }

    @Test fun failedActionEmitsActionFailedAndKeepsTheButton() = runTest {
        api.result = AiResult.Success(CoachResponse("Do it", listOf(action("log_done"))), remaining = null)
        val vm = vm()
        vm.open()
        advanceUntilIdle()
        val message = vm.uiState.value.messages.single()
        data.failWrites = true
        vm.onAction(message.id, message.actions.single())
        advanceUntilIdle()
        assertEquals(CoachEvent.ActionFailed, vm.events.first())
        assertEquals(1, vm.uiState.value.messages.single().actions.size)
    }

    @Test fun openHabitEmitsNavigation() = runTest {
        api.result = AiResult.Success(CoachResponse("Look", listOf(action("open_habit"))), remaining = null)
        val vm = vm()
        vm.open()
        advanceUntilIdle()
        val message = vm.uiState.value.messages.single()
        vm.onAction(message.id, message.actions.single())
        advanceUntilIdle()
        assertEquals(CoachEvent.OpenHabit(7), vm.events.first())
    }

    @Test fun errorsAndRetryDoNotDuplicateTheUserBubble() = runTest {
        api.result = AiResult.Offline
        val vm = vm()
        vm.onInputChange("hello coach")
        vm.onSend()
        advanceUntilIdle()
        assertEquals(AiResult.Offline, vm.uiState.value.error)
        assertFalse(vm.uiState.value.isTyping)
        assertEquals(1, vm.uiState.value.messages.size)

        api.result = AiResult.Success(CoachResponse("Back online"), remaining = 3)
        vm.onRetry()
        advanceUntilIdle()
        assertEquals(2, api.requests.size)
        assertEquals(api.requests[0].messages, api.requests[1].messages)
        assertEquals(listOf(CoachRole.USER, CoachRole.COACH), vm.uiState.value.messages.map { it.role })
        assertEquals(null, vm.uiState.value.error)
        assertEquals(3, vm.uiState.value.remaining)
    }

    @Test fun retryRepeatsTheOpeningRequest() = runTest {
        api.result = AiResult.Offline
        val vm = vm()
        vm.open()
        advanceUntilIdle()
        api.result = AiResult.Success(CoachResponse("Hi"), remaining = null)
        vm.onRetry()
        advanceUntilIdle()
        assertTrue(api.requests.all { it.messages.isEmpty() })
        assertEquals(listOf(CoachRole.COACH), vm.uiState.value.messages.map { it.role })
    }

    @Test fun consentFlow() = runTest {
        val vm = vm(Settings(consent = false))
        vm.open()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.needsConsent)
        assertFalse(vm.uiState.value.isTyping)
        assertTrue(api.requests.isEmpty())

        vm.onConsentAllowed()
        advanceUntilIdle()
        assertEquals(1, consentGranted)
        assertFalse(vm.uiState.value.needsConsent)
        assertEquals(1, api.requests.size)
        assertEquals(1, vm.uiState.value.messages.size)
    }

    @Test fun consentDismissedClosesTheSheet() = runTest {
        val vm = vm(Settings(consent = false))
        vm.open()
        advanceUntilIdle()
        vm.onConsentDismissed()
        assertFalse(vm.uiState.value.needsConsent)
        assertEquals(0, consentGranted)
    }

    @Test fun resetClearsTheConversationAndDropsLateReplies() = runTest {
        api.gate = CompletableDeferred()
        val vm = vm()
        vm.open()
        advanceUntilIdle()
        vm.reset()
        api.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(CoachUiState(), vm.uiState.value)

        api.gate = null
        vm.open() // a fresh opening after reset
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.messages.size)
    }
}
