package com.habitly.app.ui.review

import com.habitly.app.MainDispatcherRule
import com.habitly.app.ai.AiActionDto
import com.habitly.app.ai.AiApi
import com.habitly.app.ai.AiContextSource
import com.habitly.app.ai.AiGateway
import com.habitly.app.ai.AiResult
import com.habitly.app.ai.AiSettingsSource
import com.habitly.app.ai.CoachRequest
import com.habitly.app.ai.CoachResponse
import com.habitly.app.ai.ReviewPageDto
import com.habitly.app.ai.ReviewRequest
import com.habitly.app.ai.ReviewResponse
import com.habitly.app.ai.SuggestRequest
import com.habitly.app.ai.SuggestResponse
import com.habitly.app.ai.actions.AiActionRunner
import com.habitly.app.ai.actions.AiActionValidator
import com.habitly.app.ai.actions.ValidAction
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
import com.habitly.app.ui.stats.DaySlice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

// "Now" is Monday 5 Oct 2026, 09:00 — the review covers Mon 28 Sep .. Sun 4 Oct.
private val NOW = LocalDateTime.of(2026, 10, 5, 9, 0)
private val WEEK = LocalDate.of(2026, 9, 28)

private fun habit(id: Long, name: String, color: String, createdOn: LocalDate = WEEK.minusDays(20)) = HabitEntity(
    id = id, name = name, icon = "run", colorHex = color, category = "Fitness",
    frequencyType = FrequencyType.DAILY, frequencyDays = null, intervalDays = null, reminderTime = null,
    createdAt = createdOn.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), isArchived = false, sortOrder = id.toInt(),
)

private fun log(habitId: Long, date: LocalDate, status: CompletionStatus) =
    HabitLogEntity(habitId = habitId, date = date.toString(), status = status, completedAt = null, note = null)

private class FakeData(habits: List<HabitEntity>, logs: List<HabitLogEntity>) : HabitDataSource {
    val habits = MutableStateFlow(habits)
    val logs = MutableStateFlow(logs)
    val statusCalls = mutableListOf<Triple<Long, LocalDate, CompletionStatus?>>()

    override fun observeAllHabits(): Flow<List<HabitEntity>> = habits
    override fun observeAllLogs(): Flow<List<HabitLogEntity>> = logs
    override suspend fun getHabit(habitId: Long): HabitEntity? = habits.value.firstOrNull { it.id == habitId }
    override suspend fun setStatus(habitId: Long, date: LocalDate, status: CompletionStatus?, note: String?): Set<AchievementType> {
        statusCalls += Triple(habitId, date, status)
        return emptySet()
    }

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

private fun scripted() = ReviewResponse(
    listOf(
        ReviewPageDto("summary", "Solid week", "You kept most of it."),
        ReviewPageDto("win", "Run is on fire", "Seven days straight.", habitId = 1),
        ReviewPageDto("pattern", "Weekends dip", "Saturdays slip.", habitId = 2),
        ReviewPageDto("tip", "Log it now", "Quick win.", habitId = 1, action = AiActionDto("log_done", 1, label = "Log Run")),
        ReviewPageDto("tip", "Ghost habit", "Nope.", habitId = 99, action = AiActionDto("log_done", 99, label = "Log ghost")),
    ),
)

private class FakeApi : AiApi {
    val requests = mutableListOf<ReviewRequest>()
    var result: AiResult<ReviewResponse> = AiResult.Success(scripted(), remaining = 6)

    override suspend fun weeklyReview(request: ReviewRequest): AiResult<ReviewResponse> {
        requests += request
        return result
    }
    override suspend fun suggestHabits(request: SuggestRequest): AiResult<SuggestResponse> = AiResult.Failed("unused")
    override suspend fun coach(request: CoachRequest): AiResult<CoachResponse> = AiResult.Failed("unused")
}

class WeekReviewViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val api = FakeApi()
    private val cache = MutableStateFlow<String?>(null)
    private var saves = 0
    private var consentGranted = 0

    private fun defaultHabits() = listOf(habit(1, "Run", "#FF922B"), habit(2, "Read", "#4DABF7"))

    private fun defaultLogs(): List<HabitLogEntity> {
        val logs = mutableListOf<HabitLogEntity>()
        for (i in 0L..6L) logs += log(1, WEEK.plusDays(i), CompletionStatus.DONE)
        logs += log(2, WEEK.plusDays(1), CompletionStatus.SKIPPED)
        logs += log(2, WEEK.plusDays(2), CompletionStatus.DONE)
        return logs
    }

    private fun vm(
        data: FakeData = FakeData(defaultHabits(), defaultLogs()),
        settings: Settings = Settings(),
    ): Pair<WeekReviewViewModel, FakeData> {
        val viewModel = WeekReviewViewModel(
            gateway = AiGateway(settings, api, api),
            context = AiContextSource(data),
            data = data,
            cacheJson = cache,
            saveCacheJson = { saves++; cache.value = it },
            weekStart = flowOf(DayOfWeek.MONDAY),
            validation = { AiActionValidator.snapshot(data) },
            runner = AiActionRunner(data),
            allowConsent = { consentGranted++; settings.aiConsent.value = true },
            now = { NOW },
            zone = ZoneOffset.UTC,
        )
        return viewModel to data
    }

    @Test fun usesCacheWithoutCalling() = runTest {
        cache.value = ReviewCache.encode(mapOf("2026-09-28" to scripted()))
        val (vm) = vm()
        advanceUntilIdle()
        assertTrue(api.requests.isEmpty())
        assertFalse(vm.uiState.value.isLoading)
        assertEquals(6, vm.uiState.value.pages.size) // 5 from the cache + close
    }

    @Test fun fetchesAndCachesWhenMissing() = runTest {
        val (vm) = vm()
        advanceUntilIdle()
        val req = api.requests.single()
        assertEquals("2026-09-28", req.weekStart)
        assertEquals(2, req.habits.size)
        assertEquals(1, saves)
        assertTrue(ReviewCache.decode(cache.value).containsKey("2026-09-28"))

        val state = vm.uiState.value
        assertEquals(WEEK, state.weekStart)
        assertEquals("28 Sep – 4 Oct", state.weekLabel)
        assertEquals(6, state.pages.size)
        val close = state.pages.last()
        assertEquals(PageKind.CLOSE, close.kind)
        assertEquals("See you next week", close.headline)
        assertTrue(close.body.startsWith("Your next review is ready on "))
        assertTrue(close.body.endsWith(" evening."))
        assertEquals(6, state.remaining)
    }

    @Test fun weekLabelCollapsesTheMonthWithinOneMonth() = runTest {
        val data = FakeData(defaultHabits(), defaultLogs())
        val viewModel = WeekReviewViewModel(
            AiGateway(Settings(), api, api), AiContextSource(data), data, cache, { cache.value = it }, flowOf(DayOfWeek.MONDAY),
            { AiActionValidator.snapshot(data) }, AiActionRunner(data), {}, now = { LocalDateTime.of(2026, 9, 28, 9, 0) }, zone = ZoneOffset.UTC,
        )
        advanceUntilIdle()
        assertEquals("21–27 Sep", viewModel.uiState.value.weekLabel)
    }

    @Test fun pagesAreMappedWithVisualsAndValidatedActions() = runTest {
        val (vm) = vm()
        advanceUntilIdle()
        val pages = vm.uiState.value.pages

        assertEquals(listOf(PageKind.SUMMARY, PageKind.WIN, PageKind.PATTERN, PageKind.TIP, PageKind.TIP, PageKind.CLOSE), pages.map { it.kind })
        val win = pages[1].habit!!
        assertEquals("Run", win.name)
        assertEquals("run", win.iconKey)
        assertEquals("#FF922B", win.colorHex)
        assertEquals(7, win.weekdayRates.size)
        assertTrue(win.currentStreak >= 7)
        assertTrue(win.weekdayRates.all { it in 0f..1f })

        assertEquals(ValidAction.LogDone(1, "Log Run"), pages[3].action)
        // Unknown habit: the page stays, the visual and action do not.
        assertNull(pages[4].habit)
        assertNull(pages[4].action)
        assertEquals("Ghost habit", pages[4].headline)
        // Actions only belong on tips.
        assertNull(pages[0].action)
    }

    @Test fun stacksForSummary() = runTest {
        val (vm) = vm()
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals((0L..6L).map { WEEK.plusDays(it) }, state.stackDays)
        assertEquals(2, state.slotCount)
        assertEquals(listOf(DaySlice("#FF922B", false, "Run")), state.stacks.getValue(WEEK))
        assertEquals(
            listOf(DaySlice("#FF922B", false, "Run"), DaySlice("#4DABF7", true, "Read")),
            state.stacks.getValue(WEEK.plusDays(1)),
        )
        assertEquals(
            listOf(DaySlice("#FF922B", false, "Run"), DaySlice("#4DABF7", false, "Read")),
            state.stacks.getValue(WEEK.plusDays(2)),
        )
    }

    @Test fun applyRunsTheActionOnceAndEmitsAnEvent() = runTest {
        val (vm, data) = vm()
        advanceUntilIdle()
        vm.onApply(3)
        advanceUntilIdle()
        assertEquals(listOf(Triple(1L, NOW.toLocalDate(), CompletionStatus.DONE)), data.statusCalls)
        assertTrue(vm.uiState.value.pages[3].applied)
        val event = vm.events.first()
        assertTrue(event is ReviewEvent.Applied)
        assertEquals("Logged Run", (event as ReviewEvent.Applied).message)

        vm.onApply(3)
        advanceUntilIdle()
        assertEquals(1, data.statusCalls.size)
        vm.onApply(0) // a page without an action
        vm.onApply(42) // out of range
        advanceUntilIdle()
        assertEquals(1, data.statusCalls.size)
    }

    @Test fun regenerateBypassesTheCacheAndReplacesIt() = runTest {
        cache.value = ReviewCache.encode(mapOf("2026-09-28" to scripted()))
        val (vm) = vm()
        advanceUntilIdle()
        assertTrue(api.requests.isEmpty())

        api.result = AiResult.Success(
            ReviewResponse(
                listOf(
                    ReviewPageDto("summary", "Fresh take", "New."),
                    ReviewPageDto("win", "W", "w", habitId = 1),
                    ReviewPageDto("tip", "T", "t", habitId = 1),
                ),
            ),
            remaining = 5,
        )
        vm.onRegenerate()
        advanceUntilIdle()
        assertEquals(1, api.requests.size)
        assertEquals("Fresh take", vm.uiState.value.pages.first().headline)
        assertEquals("Fresh take", ReviewCache.decode(cache.value).getValue("2026-09-28").pages.first().headline)
    }

    @Test fun notEnoughHistory() = runTest {
        val data = FakeData(listOf(habit(1, "Run", "#FF922B", createdOn = NOW.toLocalDate().minusDays(1))), emptyList())
        val (vm) = vm(data)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.notEnoughHistory)
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(api.requests.isEmpty())
    }

    @Test fun consentFlow() = runTest {
        val (vm) = vm(settings = Settings(consent = false))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.needsConsent)
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(api.requests.isEmpty())

        vm.onConsentAllowed()
        advanceUntilIdle()
        assertEquals(1, consentGranted)
        assertFalse(vm.uiState.value.needsConsent)
        assertEquals(1, api.requests.size)
        assertTrue(vm.uiState.value.pages.isNotEmpty())
    }

    @Test fun errorsAndRetry() = runTest {
        api.result = AiResult.Offline
        val (vm) = vm()
        advanceUntilIdle()
        assertEquals(AiResult.Offline, vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.pages.isEmpty())

        api.result = AiResult.Success(scripted(), remaining = 3)
        vm.onRetry()
        advanceUntilIdle()
        assertNull(vm.uiState.value.error)
        assertNotNull(vm.uiState.value.pages.firstOrNull())
        assertEquals(2, api.requests.size)
    }
}
