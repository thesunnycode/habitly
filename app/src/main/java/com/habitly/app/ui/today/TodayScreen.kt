package com.habitly.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.core.snap
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.repository.HabitWithStats
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.FrequencyType
import com.habitly.app.domain.HabitTemplate
import com.habitly.app.domain.HabitTemplates
import com.habitly.app.domain.label
import com.habitly.app.ui.components.Celebration
import com.habitly.app.ui.components.CheckState
import com.habitly.app.ui.components.EmptyState
import com.habitly.app.ui.components.HabitGridCard
import com.habitly.app.ui.components.snackbarLift
import com.habitly.app.ui.components.HabitRow
import com.habitly.app.ui.components.HabitStatusSheet
import com.habitly.app.ui.components.ProgressRing
import com.habitly.app.ui.components.SecondaryButton
import com.habitly.app.ui.components.SectionLabel
import com.habitly.app.ui.components.WeekStrip
import com.habitly.app.ui.theme.HabitlyTheme
import com.habitly.app.ui.theme.LocalHabitlyColors
import com.habitly.app.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val EYEBROW_DATE = DateTimeFormatter.ofPattern("EEE d MMM")
private val SPOKEN_DATE = DateTimeFormatter.ofPattern("EEEE d MMMM")

/** How long a just-checked habit stays put in the due list showing its tick before moving to Done. */
internal const val PIN_DURATION_MS = 1200L

/**
 * Splits today's habits into the (due, done) lists to render. A habit in [pinned] (id to its original index in
 * the due list) that is now done stays in the due list at that index with its real status, so the tick is seen
 * where the user tapped. Pinned habits that are not done (un-ticked, or a goal still in progress) are already
 * in [due] and are left alone.
 */
internal fun arrangeToday(
    due: List<HabitWithStats>,
    done: List<HabitWithStats>,
    pinned: Map<Long, Int>,
): Pair<List<HabitWithStats>, List<HabitWithStats>> {
    if (pinned.isEmpty()) return due to done
    val (stay, moved) = done.partition { it.habit.id in pinned }
    if (stay.isEmpty()) return due to done
    val arranged = due.toMutableList()
    stay.sortedBy { pinned.getValue(it.habit.id) }.forEach { arranged.add(pinned.getValue(it.habit.id).coerceIn(0, arranged.size), it) }
    return arranged to moved
}

@Composable
fun TodayScreen(
    uiState: TodayUiState,
    onCheck: (HabitWithStats) -> Unit,
    onSetStatus: (Long, CompletionStatus?, String?) -> Unit,
    onHabitClick: (Long) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onAddHabit: () -> Unit,
    onTemplatePicked: (HabitTemplate?) -> Unit,
    onCelebrationShown: () -> Unit,
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    now: LocalTime = LocalTime.now(),
    onCreateWithAi: () -> Unit = {},
) {
    var showTemplates by rememberSaveable { mutableStateOf(false) }
    var statusTargetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var doneExpanded by rememberSaveable { mutableStateOf(true) }
    var notDueExpanded by rememberSaveable { mutableStateOf(false) }
    var showForecastInfo by rememberSaveable { mutableStateOf(false) }
    val forecast = uiState.forecast?.takeIf { uiState.dueCount > 0 }
    // Once the forecast goes away (past day, setting off, nothing due) the sheet must not reappear with it.
    LaunchedEffect(forecast == null) { if (forecast == null) showForecastInfo = false }

    val pinned = remember { mutableStateMapOf<Long, Int>() }
    val scope = rememberCoroutineScope()
    val (dueList, doneList) = arrangeToday(uiState.due, uiState.done, pinned)
    val pinCheck: (HabitWithStats) -> Unit = { habit ->
        val id = habit.habit.id
        val index = uiState.due.indexOfFirst { it.habit.id == id }
        if (index >= 0 && id !in pinned) {
            pinned[id] = index
            scope.launch {
                delay(PIN_DURATION_MS)
                pinned.remove(id)
            }
        }
        onCheck(habit)
    }

    val today = uiState.weekDays.lastOrNull() ?: uiState.selectedDate
    val lists: LazyListScope.() -> Unit = {
        habitSections(
            uiState = uiState,
            dueList = dueList,
            doneList = doneList,
            today = today,
            doneExpanded = doneExpanded,
            onToggleDone = { doneExpanded = !doneExpanded },
            notDueExpanded = notDueExpanded,
            onToggleNotDue = { notDueExpanded = !notDueExpanded },
            onCheck = pinCheck,
            onHabitClick = onHabitClick,
            onLongPress = { statusTargetId = it.habit.id },
            onAddHabit = onAddHabit,
            onTemplatePicked = onTemplatePicked,
            onCreateWithAi = onCreateWithAi,
        )
    }

    Box(Modifier.fillMaxSize()) {
        if (windowWidthSizeClass == WindowWidthSizeClass.Expanded) {
            Row(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                Column(
                    modifier = Modifier
                        .weight(0.45f)
                        .statusBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(end = 20.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    TodayHeader(uiState, now)
                    TodayWeekStrip(uiState, today, onSelectDate)
                    forecast?.let { ForecastStrip(it, onHabitClick = onHabitClick, onInfo = { showForecastInfo = true }) }
                    uiState.atRisk?.let { AtRiskBanner(it) }
                }
                LazyColumn(
                    modifier = Modifier.weight(0.55f).statusBarsPadding(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = lists,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "header") { TodayHeader(uiState, now, Modifier.statusBarsPadding()) }
                item(key = "week") { TodayWeekStrip(uiState, today, onSelectDate, Modifier.padding(top = 6.dp)) }
                forecast?.let { f ->
                    item(key = "forecast") { ForecastStrip(f, onHabitClick = onHabitClick, onInfo = { showForecastInfo = true }) }
                }
                uiState.atRisk?.let { habit -> item(key = "risk") { AtRiskBanner(habit) } }
                lists()
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showTemplates = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).offset(y = -snackbarLift()),
            containerColor = LocalHabitlyColors.current.primaryButton,
            contentColor = LocalHabitlyColors.current.onPrimaryButton,
            shape = CircleShape,
            text = { Text("New", style = MaterialTheme.typography.labelLarge) },
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
        )

        Celebration(
            visible = uiState.showCelebration,
            title = "All done for today",
            message = "Every habit ticked. See you tomorrow.",
            onDismiss = onCelebrationShown,
        )
    }

    if (showTemplates) {
        TemplateSheet(
            onDismiss = { showTemplates = false },
            onPick = { template ->
                showTemplates = false
                onTemplatePicked(template)
            },
            onCreateWithAi = {
                showTemplates = false
                onCreateWithAi()
            },
        )
    }

    if (showForecastInfo && forecast != null) {
        ForecastInfoSheet(forecast, onDismiss = { showForecastInfo = false })
    }

    val statusTarget = statusTargetId?.let { id ->
        (uiState.due + uiState.done + uiState.notDue).firstOrNull { it.habit.id == id }
    }
    if (statusTarget != null) {
        HabitStatusSheet(
            habitName = statusTarget.habit.name,
            current = statusTarget.todayStatus,
            initialNote = statusTarget.todayNote,
            onSelect = { status, note ->
                statusTargetId = null
                onSetStatus(statusTarget.habit.id, status, note)
            },
            onDismiss = { statusTargetId = null },
        )
    }
}

private fun LazyListScope.habitSections(
    uiState: TodayUiState,
    dueList: List<HabitWithStats>,
    doneList: List<HabitWithStats>,
    today: LocalDate,
    doneExpanded: Boolean,
    onToggleDone: () -> Unit,
    notDueExpanded: Boolean,
    onToggleNotDue: () -> Unit,
    onCheck: (HabitWithStats) -> Unit,
    onHabitClick: (Long) -> Unit,
    onLongPress: (HabitWithStats) -> Unit,
    onAddHabit: () -> Unit,
    onTemplatePicked: (HabitTemplate?) -> Unit,
    onCreateWithAi: () -> Unit,
) {
    if (uiState.isEmpty) {
        item(key = "empty") { TodayEmptyState(onAddHabit, onTemplatePicked, onCreateWithAi) }
        return
    }

    items(dueList, key = { it.habit.id }) { habit ->
        TodayCard(habit, dueCheckState(habit), today, uiState, onCheck, onHabitClick, onLongPress, animatedItem())
    }

    if (doneList.isNotEmpty()) {
        item(key = "done-label") {
            CollapsibleLabel("Done", doneList.size, doneExpanded, onToggleDone)
        }
        if (doneExpanded) {
            items(doneList, key = { it.habit.id }) { habit ->
                val state = if (habit.todayStatus == CompletionStatus.SKIPPED) CheckState.SKIPPED else CheckState.DONE
                TodayCard(habit, state, today, uiState, onCheck, onHabitClick, onLongPress, animatedItem())
            }
        }
    }

    if (uiState.notDue.isNotEmpty()) {
        item(key = "notdue-label") {
            CollapsibleLabel("Not today", uiState.notDue.size, notDueExpanded, onToggleNotDue)
        }
        if (notDueExpanded) {
            items(uiState.notDue, key = { "notdue-${it.habit.id}" }) { habit ->
                HabitRow(
                    modifier = animatedItem(),
                    name = habit.habit.name,
                    iconKey = habit.habit.icon,
                    colorHex = habit.habit.colorHex,
                    supporting = habit.schedule.label(),
                    last7 = habit.last7DaysStatus.map { it.toCheckState() },
                    onClick = { onHabitClick(habit.habit.id) },
                )
            }
        }
    }
}

@Composable
private fun TodayCard(
    habit: HabitWithStats,
    state: CheckState,
    today: LocalDate,
    uiState: TodayUiState,
    onCheck: (HabitWithStats) -> Unit,
    onHabitClick: (Long) -> Unit,
    onLongPress: (HabitWithStats) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isGoal = habit.habit.targetCount != null
    HabitGridCard(
        modifier = modifier,
        name = habit.habit.name,
        iconKey = habit.habit.icon,
        colorHex = habit.habit.colorHex,
        meta = cardMeta(habit),
        checkState = state,
        levels = habit.levels,
        onCheck = { onCheck(habit) },
        onClick = { onHabitClick(habit.habit.id) },
        onLongClick = { onLongPress(habit) },
        endDate = today,
        weekStart = uiState.weekStart,
        streakDays = if (isGoal) 0 else habit.currentStreak,
    )
}

/** Slides neighbours smoothly as cards move between sections; instant when the user prefers reduced motion. */
@Composable
private fun LazyItemScope.animatedItem(): Modifier =
    if (LocalReduceMotion.current) Modifier.animateItem(fadeInSpec = null, placementSpec = snap(), fadeOutSpec = null)
    else Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)

/** A due goal habit with some units logged is mid-way; anything else due is simply not done yet. */
private fun dueCheckState(habit: HabitWithStats): CheckState =
    if (habit.todayStatus == CompletionStatus.DONE) CheckState.DONE  // pinned: just checked, not yet moved to Done
    else if (habit.habit.targetCount != null && habit.todayProgress > 0) CheckState.IN_PROGRESS else CheckState.EMPTY

/** "12 days · Daily", "Mon, Wed, Fri" (no streak yet) or, for a goal habit, "3 / 8 cups". */
internal fun cardMeta(habit: HabitWithStats): String {
    val target = habit.habit.targetCount
    if (target != null) {
        return listOfNotNull("${habit.todayProgress} / $target", habit.habit.unit).joinToString(" ")
    }
    val schedule = habit.schedule.label()
    val streak = habit.currentStreak
    return if (streak > 0) "$streak ${if (streak == 1) "day" else "days"} · $schedule" else schedule
}

private fun CompletionStatus?.toCheckState(): CheckState = when (this) {
    CompletionStatus.DONE -> CheckState.DONE
    CompletionStatus.SKIPPED -> CheckState.SKIPPED
    CompletionStatus.PARTIAL -> CheckState.IN_PROGRESS
    CompletionStatus.MISSED, null -> CheckState.EMPTY
}

@Composable
private fun TodayHeader(uiState: TodayUiState, now: LocalTime, modifier: Modifier = Modifier) {
    val date = uiState.selectedDate
    val eyebrow = if (uiState.isViewingToday) date.format(EYEBROW_DATE) else "Viewing ${date.format(EYEBROW_DATE)}"
    val title = if (uiState.isViewingToday) "Today" else date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            val name = uiState.userName
            if (!name.isNullOrBlank()) {
                Text(
                    text = "${greetingFor(now)}, $name",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (uiState.dueCount > 0) {
            Spacer(Modifier.width(12.dp))
            ProgressRing(
                progress = uiState.doneCount.toFloat() / uiState.dueCount,
                label = "${uiState.doneCount}/${uiState.dueCount}",
                description = "${uiState.doneCount} of ${uiState.dueCount} done",
                size = 56.dp,
            )
        }
    }
}

private fun greetingFor(now: LocalTime): String = when {
    now.hour < 12 -> "Good morning"
    now.hour < 17 -> "Good afternoon"
    else -> "Good evening"
}

@Composable
private fun TodayWeekStrip(
    uiState: TodayUiState,
    today: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    WeekStrip(
        days = uiState.weekDays,
        selected = uiState.selectedDate,
        completion = { uiState.dayCompletion[it] ?: 0f },
        describe = { day ->
            val share = uiState.dayCompletion[day] ?: 0f
            val progress = when {
                share >= 1f -> "all done"
                share > 0f -> "partly done"
                else -> "nothing done"
            }
            val suffix = if (day == today) ", today" else ""
            "${day.format(SPOKEN_DATE)}$suffix, $progress"
        },
        onSelect = onSelectDate,
        today = today,
        modifier = modifier,
    )
}

@Composable
private fun AtRiskBanner(habit: HabitWithStats) {
    val warning = LocalHabitlyColors.current.warning
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(warning.copy(alpha = 0.14f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = warning, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = "‘${habit.habit.name}’ streak (${habit.currentStreak}) ends tonight",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CollapsibleLabel(text: String, count: Int, expanded: Boolean, onToggle: () -> Unit) {
    SectionLabel(
        text = text,
        count = count,
        modifier = Modifier.padding(top = 14.dp),
        trailing = {
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse $text" else "Expand $text",
                    tint = LocalHabitlyColors.current.onSurfaceFaint,
                )
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayEmptyState(onAddHabit: () -> Unit, onTemplatePicked: (HabitTemplate?) -> Unit, onCreateWithAi: () -> Unit) {
    val starters = remember { HabitTemplates.groups.first().templates.take(3) }
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyState(
            icon = Icons.Rounded.GridView,
            title = "Start with one habit",
            message = "Pick something small you can do today.",
            actionLabel = "Create a habit",
            onAction = onAddHabit,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            starters.forEach { template ->
                SecondaryButton(text = template.name, onClick = { onTemplatePicked(template) })
            }
        }
        TextButton(onClick = onCreateWithAi) { Text("✨ Describe a goal and let AI suggest habits") }
    }
}

// ---- Previews ----

private fun previewHabit(id: Long, name: String, icon: String, color: String, streak: Int, status: CompletionStatus?, due: Boolean = true) =
    HabitWithStats(
        habit = HabitEntity(id, name, icon, color, "Health", FrequencyType.DAILY, null, null, null, 0L, false),
        currentStreak = streak, bestStreak = streak, completionRate = 0.8f,
        isCompletedToday = status == CompletionStatus.DONE, isDueToday = due, todayStatus = status,
        levels = (0 until 112).associate { LocalDate.of(2026, 9, 29).minusDays(it.toLong()) to (it * (id.toInt() + 2) % 5) },
    )

@Composable
private fun TodayScreenPreviewContent(empty: Boolean) {
    val today = LocalDate.of(2026, 9, 29)
    val state = if (empty) {
        TodayUiState(isLoading = false, selectedDate = today, weekDays = (6 downTo 0).map { today.minusDays(it.toLong()) })
    } else {
        TodayUiState(
            isLoading = false,
            selectedDate = today,
            weekDays = (6 downTo 0).map { today.minusDays(it.toLong()) },
            dayCompletion = mapOf(today.minusDays(1) to 1f, today.minusDays(2) to 0.5f),
            due = listOf(previewHabit(1, "Workout", "fitness_center", "#FF6B6B", 4, null)),
            done = listOf(previewHabit(2, "Read", "menu_book", "#9775FA", 12, CompletionStatus.DONE)),
            notDue = listOf(previewHabit(3, "Weekly review", "star", "#CED4DA", 0, null, due = false)),
            doneCount = 1, dueCount = 2, userName = "Sunny",
            atRisk = previewHabit(1, "Workout", "fitness_center", "#FF6B6B", 4, null),
        )
    }
    Surface(color = MaterialTheme.colorScheme.background) {
        TodayScreen(state, {}, { _, _, _ -> }, {}, {}, {}, {}, {}, now = LocalTime.of(19, 0))
    }
}

@Preview @Composable private fun TodayScreenDarkPreview() = HabitlyTheme(darkTheme = true) { TodayScreenPreviewContent(empty = false) }
@Preview @Composable private fun TodayScreenLightPreview() = HabitlyTheme(darkTheme = false) { TodayScreenPreviewContent(empty = false) }
@Preview @Composable private fun TodayScreenEmptyPreview() = HabitlyTheme(darkTheme = true) { TodayScreenPreviewContent(empty = true) }
