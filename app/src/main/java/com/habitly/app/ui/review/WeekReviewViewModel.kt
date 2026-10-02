package com.habitly.app.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitly.app.ai.AiContextSource
import com.habitly.app.ai.AiGateway
import com.habitly.app.ai.AiResult
import com.habitly.app.ai.HabitSummaryDto
import com.habitly.app.ai.ReviewRequest
import com.habitly.app.ai.ReviewResponse
import com.habitly.app.ai.actions.ActionOutcome
import com.habitly.app.ai.actions.AiActionRunner
import com.habitly.app.ai.actions.AiActionValidator
import com.habitly.app.ai.actions.ValidationContext
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.local.HabitLogEntity
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.ui.stats.DaySlice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class WeekReviewViewModel(
    private val gateway: AiGateway,
    private val context: AiContextSource,
    private val data: HabitDataSource,
    private val cacheJson: Flow<String?>,
    private val saveCacheJson: suspend (String) -> Unit,
    private val weekStart: Flow<DayOfWeek>,
    private val validation: suspend () -> ValidationContext,
    private val runner: AiActionRunner,
    private val allowConsent: suspend () -> Unit,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeekReviewUiState(isLoading = true))
    val uiState: StateFlow<WeekReviewUiState> = _uiState.asStateFlow()

    private val _events = Channel<ReviewEvent>(Channel.BUFFERED)
    val events: Flow<ReviewEvent> = _events.receiveAsFlow()

    private val applying = mutableSetOf<Int>()
    private var closingWeekday = DayOfWeek.SUNDAY
    private var loadJob: Job? = null

    init {
        load(force = false)
    }

    fun onRegenerate() = load(force = true)

    fun onRetry() = load(force = false)

    fun onConsentAllowed() {
        viewModelScope.launch {
            try {
                allowConsent()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(needsConsent = false, error = AiResult.Failed("consent not saved")) }
                return@launch
            }
            _uiState.update { it.copy(needsConsent = false) }
            load(force = false)
        }
    }

    fun onConsentDismissed() = _uiState.update { it.copy(needsConsent = false) }

    fun onApply(pageIndex: Int) {
        val page = _uiState.value.pages.getOrNull(pageIndex) ?: return
        val action = page.action ?: return
        if (page.applied || !applying.add(pageIndex)) return
        viewModelScope.launch {
            try {
                when (val outcome = runner.run(action, now().toLocalDate())) {
                    is ActionOutcome.Applied -> {
                        _uiState.update { state ->
                            state.copy(pages = state.pages.mapIndexed { i, p -> if (i == pageIndex) p.copy(applied = true) else p })
                        }
                        _events.send(ReviewEvent.Applied(outcome.message, outcome.undo))
                    }
                    is ActionOutcome.Navigate -> Unit
                    ActionOutcome.Failed -> _events.send(ReviewEvent.ActionFailed)
                }
            } finally {
                applying.remove(pageIndex)
            }
        }
    }

    private fun load(force: Boolean) {
        if (loadJob?.isActive == true) return
        _uiState.update {
            it.copy(isLoading = true, pages = emptyList(), error = null, needsConsent = false, notEnoughHistory = false)
        }
        loadJob = viewModelScope.launch {
            try {
                run(force)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = AiResult.Failed("review failed")) }
            }
        }
    }

    private suspend fun run(force: Boolean) {
        val ws = weekStart.first()
        closingWeekday = ws.minus(1)
        val current = now()
        val today = current.toLocalDate()
        val reviewStart = WeekReviewSchedule.reviewWeekStart(current, ws)
        val asOf = WeekReviewSchedule.reviewAsOf(reviewStart, today)
        val key = reviewStart.toString()
        _uiState.update { it.copy(weekStart = reviewStart, weekLabel = weekLabel(reviewStart)) }

        val habits = data.observeAllHabits().first()
        val logs = data.observeAllLogs().first()
        if (WeekReviewSchedule.trackingDays(habits, logs, today, zone) < WeekReviewSchedule.MIN_TRACKING_DAYS) {
            _uiState.update { it.copy(isLoading = false, notEnoughHistory = true) }
            return
        }

        val cache = ReviewCache.decode(cacheJson.first())
        val cached = cache[key]
        if (!force && cached != null) {
            present(cached, reviewStart, asOf, habits, logs)
            return
        }

        val summaries = context.summaries(asOf)
        when (val result = gateway.call { weeklyReview(ReviewRequest(key, summaries)) }) {
            is AiResult.Success -> {
                try {
                    saveCacheJson(ReviewCache.encode(ReviewCache.put(cache, key, result.value)))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Not caching only costs a repeat request next time.
                }
                _uiState.update { it.copy(remaining = result.remaining ?: it.remaining) }
                present(result.value, reviewStart, asOf, habits, logs, summaries)
            }
            is AiResult.ConsentRequired -> _uiState.update { it.copy(isLoading = false, needsConsent = true) }
            else -> _uiState.update { it.copy(isLoading = false, error = result) }
        }
    }

    private suspend fun present(
        review: ReviewResponse,
        reviewStart: LocalDate,
        asOf: LocalDate,
        habits: List<HabitEntity>,
        logs: List<HabitLogEntity>,
        fetched: List<HabitSummaryDto>? = null,
    ) {
        val summaries = fetched ?: context.summaries(asOf)
        val active = habits.filter { !it.isArchived }
        val byId = active.associateBy { it.id }
        val visuals = summaries.mapNotNull { s ->
            val habit = byId[s.id] ?: return@mapNotNull null
            HabitVisual(
                id = habit.id, name = habit.name, iconKey = habit.icon, colorHex = habit.colorHex,
                currentStreak = s.currentStreak,
                weekdayRates = DayOfWeek.entries.map { day ->
                    val stat = s.weekdays.firstOrNull { it.day == day.name }
                    if (stat == null || stat.due == 0) 0f else stat.done.toFloat() / stat.due
                },
            )
        }.associateBy { it.id }

        val ctx = try {
            validation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val today = now().toLocalDate()
        val pages = review.pages.mapNotNull { dto ->
            val kind = when (dto.kind.lowercase()) {
                "summary" -> PageKind.SUMMARY
                "win" -> PageKind.WIN
                "pattern" -> PageKind.PATTERN
                "tip" -> PageKind.TIP
                else -> return@mapNotNull null
            }
            ReviewPageUi(
                kind = kind,
                headline = dto.headline.trim(),
                body = dto.body.trim(),
                habit = dto.habitId?.let(visuals::get),
                action = if (kind == PageKind.TIP && ctx != null) dto.action?.let { AiActionValidator.validate(it, ctx, today, zone) } else null,
            )
        } + ReviewPageUi(
            kind = PageKind.CLOSE,
            headline = "See you next week",
            body = "Your next review is ready on ${closingWeekday.getDisplayName(TextStyle.FULL, Locale.getDefault())} evening.",
        )

        val days = (0L..6L).map { reviewStart.plusDays(it) }
        val statusByDay = logs.groupBy { it.habitId }.mapValues { (_, l) -> l.associate { it.date to it.status } }
        val stacks = days.associateWith { day ->
            active.sortedBy { it.sortOrder }.mapNotNull { habit ->
                when (statusByDay[habit.id]?.get(day.toString())) {
                    CompletionStatus.DONE -> DaySlice(habit.colorHex, rested = false, name = habit.name)
                    CompletionStatus.SKIPPED -> DaySlice(habit.colorHex, rested = true, name = habit.name)
                    else -> null
                }
            }
        }
        applying.clear()
        _uiState.update {
            it.copy(
                isLoading = false, pages = pages, stackDays = days, stacks = stacks, slotCount = active.size,
            )
        }
    }

    private fun weekLabel(start: LocalDate): String {
        val end = start.plusDays(6)
        val day = DateTimeFormatter.ofPattern("d", Locale.ENGLISH)
        val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
        return if (start.month == end.month) "${start.format(day)}–${end.format(dayMonth)}"
        else "${start.format(dayMonth)} – ${end.format(dayMonth)}"
    }
}
