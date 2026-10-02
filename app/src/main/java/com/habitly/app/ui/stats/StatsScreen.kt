package com.habitly.app.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.habitly.app.domain.Insight
import com.habitly.app.domain.InsightKind
import com.habitly.app.ui.components.IconTile
import com.habitly.app.ui.components.SectionLabel
import com.habitly.app.ui.components.SegmentedPill
import com.habitly.app.ui.components.StatTile
import com.habitly.app.ui.components.SettingsRow
import com.habitly.app.ui.theme.HabitlyTheme
import com.habitly.app.ui.theme.LocalHabitlyColors
import com.habitly.app.ui.theme.LocalReduceMotion
import com.habitly.app.ui.theme.habitTones
import java.time.DayOfWeek
import java.time.LocalDate

/** The habit list judges each habit against its own history, which needs a week of it. */
private const val DAYS_FOR_VERDICTS = 7

private val CARD_SHAPE = RoundedCornerShape(20.dp)
private val SCREEN_MARGIN = 20.dp

/**
 * The Stats segment of Insights (spec §11): habit filter chips, the heatmap hero, four record tiles
 * and the habit list. The W · M · Y switch lives in the Insights title row — see [StatsPeriodSwitch].
 */
@Composable
fun StatsScreen(
    uiState: StatsUiState,
    onHabitFilterChange: (Long?) -> Unit = {},
    onHabitClick: (Long) -> Unit = {},
    weekStart: DayOfWeek = DayOfWeek.MONDAY,
    onOpenWeekReview: (() -> Unit)? = null,
    weekReviewAvailable: Boolean = true,
) {
    // The chips flip the moment they are tapped, but the figures take a rebuild to catch up. Until
    // they do, keep drawing the last state whose figures matched its own selection, so a habit's
    // name never sits over another view's numbers.
    var lastConsistent by remember { mutableStateOf(uiState) }
    if (uiState.isConsistent) SideEffect { lastConsistent = uiState }
    val shown = if (uiState.isConsistent) uiState else lastConsistent
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Clear of the floating nav bar. No side padding: the chip row scrolls edge to edge.
        contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Nothing to say until the first build lands; the empty card would only flash.
        if (uiState.isLoading) return@LazyColumn
        val margin = Modifier.padding(horizontal = SCREEN_MARGIN)
        if (!uiState.hasAnyHistory) {
            item { NothingYetCard(margin) }
            return@LazyColumn
        }

        if (uiState.chips.isNotEmpty()) {
            item { HabitChipRow(uiState.chips, uiState.selectedHabitId, onHabitFilterChange) }
        }

        item { StatsHeroCard(shown, weekStart, margin) }

        if (onOpenWeekReview != null) {
            item(key = "review") {
                Surface(
                    modifier = margin.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    SettingsRow(
                        title = "Week in review",
                        icon = Icons.Rounded.AutoAwesome,
                        iconTint = MaterialTheme.colorScheme.primary,
                        supporting = if (weekReviewAvailable) "Last week, told as a story" else "Available after 3 days of tracking",
                        titleColor = if (weekReviewAvailable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = if (weekReviewAvailable) onOpenWeekReview else null,
                    )
                }
            }
        }

        item { RecordTiles(shown, margin) }

        item {
            if (uiState.daysOfHistory >= DAYS_FOR_VERDICTS) {
                HabitsCard(shown.habitVerdicts, onHabitClick, margin.padding(top = 12.dp))
            } else {
                StatsCard(margin.padding(top = 12.dp)) {
                    Text("Habit by habit", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = "How each habit is going. Unlocks on day $DAYS_FOR_VERDICTS — ${uiState.daysOfHistory} so far.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Compact W · M · Y switch for the Insights title row. The letters are read out as whole words. */
@Composable
fun StatsPeriodSwitch(selected: StatsPeriod, onSelect: (StatsPeriod) -> Unit, modifier: Modifier = Modifier) {
    SegmentedPill(
        options = StatsPeriod.entries,
        selected = selected,
        onSelect = onSelect,
        label = { it.label.take(1) },
        description = { it.label },
        modifier = modifier.width(144.dp),
    )
}

/** All + one chip per habit. The selected chip is kept in view, so a filter is never off-screen. */
@Composable
private fun HabitChipRow(chips: List<HabitChip>, selectedId: Long?, onSelect: (Long?) -> Unit) {
    val listState = rememberLazyListState()
    val reduceMotion = LocalReduceMotion.current
    // All is item 0, so a habit's index is its chip index + 1 (and a missing one falls back to All).
    val selectedIndex = chips.indexOfFirst { it.id == selectedId } + 1
    LaunchedEffect(selectedIndex) {
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
        val fullyVisible = item != null &&
            item.offset >= info.viewportStartOffset &&
            item.offset + item.size <= info.viewportEndOffset
        if (!fullyVisible) {
            if (reduceMotion) listState.scrollToItem(selectedIndex) else listState.animateScrollToItem(selectedIndex)
        }
    }
    val scheme = MaterialTheme.colorScheme
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = SCREEN_MARGIN),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            FilterPill(
                label = "All",
                dot = null,
                selected = selectedId == null,
                selectedFill = scheme.primaryContainer,
                selectedInk = scheme.primary,
                selectedText = scheme.primary,
                onClick = { onSelect(null) },
            )
        }
        itemsIndexed(chips, key = { _, chip -> chip.id }) { _, chip ->
            val tones = habitTones(chip.colorHex)
            FilterPill(
                label = chip.name,
                dot = tones.full,
                selected = chip.id == selectedId,
                selectedFill = tones.low,
                selectedInk = tones.full,
                // In light mode the full tone is only guaranteed 3:1 on white, not on its own tint,
                // so the name stays in ink there; the outline and dot carry the colour.
                selectedText = if (LocalHabitlyColors.current.isDark) tones.full else scheme.onSurface,
                onClick = { onSelect(chip.id) },
            )
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    dot: Color?,
    selected: Boolean,
    selectedFill: Color,
    selectedInk: Color,
    selectedText: Color,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = LocalHabitlyColors.current.isDark
    val border = when {
        selected -> BorderStroke(1.5.dp, selectedInk)
        isDark -> null
        else -> BorderStroke(1.dp, scheme.outline)
    }
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .height(36.dp)
            .clip(CircleShape)
            .background(if (selected) selectedFill else scheme.surface)
            .then(if (border != null) Modifier.border(border, CircleShape) else Modifier)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) selectedText else scheme.onSurface,
            maxLines = 1,
        )
    }
}

/** Two by two, equal heights. Perfect days are an all-habit figure, so one habit shows rest days. */
@Composable
private fun RecordTiles(uiState: StatsUiState, modifier: Modifier = Modifier) {
    val extras = LocalHabitlyColors.current
    val primary = MaterialTheme.colorScheme.primary
    val tile = Modifier.fillMaxHeight()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                value = "${uiState.currentStreak}",
                label = "Current streak",
                icon = Icons.Rounded.LocalFireDepartment,
                iconTint = extras.warning,
                animateFrom = 0,
                modifier = tile.weight(1f),
            )
            StatTile(
                value = "${uiState.bestStreak}",
                label = "Best ever",
                icon = Icons.Rounded.EmojiEvents,
                iconTint = primary,
                animateFrom = 0,
                modifier = tile.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (uiState.dataHabitId != null) {
                StatTile(
                    value = "${uiState.restDays}",
                    label = "Rest days",
                    icon = Icons.Rounded.Bedtime,
                    iconTint = primary,
                    animateFrom = 0,
                    modifier = tile.weight(1f),
                )
            } else {
                StatTile(
                    value = "${uiState.perfectDays}",
                    label = "Perfect days",
                    icon = Icons.Rounded.Star,
                    iconTint = primary,
                    animateFrom = 0,
                    modifier = tile.weight(1f),
                )
            }
            StatTile(
                value = "${uiState.totalCompletions}",
                label = "Total done",
                icon = Icons.Rounded.TaskAlt,
                iconTint = extras.success,
                animateFrom = 0,
                modifier = tile.weight(1f),
            )
        }
    }
}

/** Every habit, best rate first. A row opens that habit's detail; it does not change the filter. */
@Composable
private fun HabitsCard(verdicts: List<HabitVerdict>, onHabitClick: (Long) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        SectionLabel("Habits", modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        StatsCard(contentPadding = PaddingValues(vertical = 4.dp)) {
            verdicts.forEachIndexed { index, verdict ->
                if (index > 0) {
                    // Inset to the text column: 16 padding + 32 tile + 12 gap.
                    HorizontalDivider(Modifier.padding(start = 60.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                }
                HabitStatRow(verdict, onClick = { onHabitClick(verdict.id) })
            }
        }
    }
}

@Composable
private fun HabitStatRow(verdict: HabitVerdict, onClick: () -> Unit) {
    val tones = habitTones(verdict.colorHex)
    val extras = LocalHabitlyColors.current
    val judged = verdict.group != VerdictGroup.NOT_ENOUGH_DATA
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClickLabel = "Open details", role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(iconKey = verdict.iconKey, colorHex = verdict.colorHex, size = 32.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = verdict.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(extras.gridEmpty),
            ) {
                if (judged && verdict.rate > 0f) {
                    Box(
                        Modifier
                            .fillMaxWidth(verdict.rate.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(tones.full),
                    )
                }
            }
        }
        Text(
            text = if (judged) "${(verdict.rate * 100).toInt()}%" else "—",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = if (judged) Modifier else Modifier.clearAndSetSemantics { contentDescription = "Not enough data yet" },
        )
        // Fixed-width slot so the percentages line up whether or not a row has an arrow.
        Box(Modifier.width(26.dp), contentAlignment = Alignment.CenterEnd) {
            val points = verdict.delta?.takeIf { judged }?.let { (it * 100).toInt() } ?: 0
            if (points != 0) {
                val up = points > 0
                Icon(
                    imageVector = if (up) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
                    contentDescription = if (up) "Rising" else "Falling",
                    tint = if (up) extras.success else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** A surface card, outlined in light mode where there is no tonal step from the background. */
@Composable
private fun StatsCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CARD_SHAPE,
        color = MaterialTheme.colorScheme.surface,
        border = if (LocalHabitlyColors.current.isDark) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

@Composable
private fun NothingYetCard(modifier: Modifier = Modifier) {
    StatsCard(modifier) {
        Text("No stats yet", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = "Tick off a habit and this screen fills in: your completion rate, streak records and " +
                "a grid of every day, in each habit's colour.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun previewState(selected: Long?): StatsUiState {
    val today = LocalDate.of(2026, 9, 30)
    val chips = listOf(
        HabitChip(1, "Water", "#4DABF7"),
        HabitChip(2, "Read 20 min", "#9775FA"),
        HabitChip(3, "Run", "#FF922B"),
        HabitChip(4, "Meditate", "#51CF66"),
    )
    val window = (29 downTo 0).map { today.minusDays(it.toLong()) }
    val stacks = window.mapIndexedNotNull { i, day ->
        val slices = chips.filterIndexed { c, _ -> (i * 3 + c * 5) % 7 < 4 }
            .mapIndexed { c, chip -> DaySlice(chip.colorHex, rested = (i + c) % 9 == 0, name = chip.name) }
        if (slices.isEmpty()) null else day to slices
    }.toMap()
    return StatsUiState(
        period = StatsPeriod.MONTH,
        selectedHabitId = selected,
        dataHabitId = selected,
        dataPeriod = StatsPeriod.MONTH,
        selectedHabitStart = selected?.let { today.minusDays(11) },
        chips = chips,
        window = window,
        dayStacks = if (selected == null) stacks else emptyMap(),
        habitLevels = if (selected != null) window.withIndex().associate { (i, d) -> d to listOf(4, 4, 0, 4, 1, 4, 2)[i % 7] } else emptyMap(),
        insights = listOf(Insight(InsightKind.PERSONAL_BEST, "Best on Wednesdays · Sundays slip most")),
        completionRate = 0.78f,
        previousRate = 0.72f,
        daysOfHistory = 90,
        currentStreak = 12,
        bestStreak = 21,
        perfectDays = 9,
        restDays = 3,
        totalCompletions = 184,
        habitVerdicts = listOf(
            HabitVerdict(1, "Water", "#4DABF7", 0.93f, 0.05f, VerdictGroup.GOING_WELL, "water_drop"),
            HabitVerdict(2, "Read 20 min", "#9775FA", 0.8f, 0f, VerdictGroup.GOING_WELL, "menu_book"),
            HabitVerdict(3, "Run", "#FF922B", 0.52f, -0.12f, VerdictGroup.SLIPPING, "run"),
            HabitVerdict(4, "Meditate", "#51CF66", 0.4f, null, VerdictGroup.NOT_ENOUGH_DATA, "self_improvement"),
        ),
        today = today,
        isLoading = false,
    )
}

@Composable
private fun StatsPreviewContent(selected: Long?) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            StatsPeriodSwitch(StatsPeriod.MONTH, {}, Modifier.padding(start = 20.dp, top = 8.dp))
            StatsScreen(previewState(selected))
        }
    }
}

@Preview(heightDp = 1400) @Composable private fun StatsDarkPreview() = HabitlyTheme(darkTheme = true) { StatsPreviewContent(null) }
@Preview(heightDp = 1400) @Composable private fun StatsLightPreview() = HabitlyTheme(darkTheme = false) { StatsPreviewContent(null) }
@Preview(heightDp = 1400) @Composable private fun StatsHabitDarkPreview() = HabitlyTheme(darkTheme = true) { StatsPreviewContent(2) }
@Preview(heightDp = 1400) @Composable private fun StatsHabitLightPreview() = HabitlyTheme(darkTheme = false) { StatsPreviewContent(2) }
