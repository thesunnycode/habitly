package com.habitly.app.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.habitly.app.ai.AiResult
import com.habitly.app.ai.actions.ValidAction
import com.habitly.app.ai.ui.AiErrorCard
import com.habitly.app.ai.ui.AiSkeleton
import com.habitly.app.ai.ui.ConsentSheet
import com.habitly.app.ui.components.EmptyState
import com.habitly.app.ui.components.IconTile
import com.habitly.app.ui.components.PrimaryButton
import com.habitly.app.ui.stats.DaySlice
import com.habitly.app.ui.stats.GridModel
import com.habitly.app.ui.stats.StatsPeriod
import com.habitly.app.ui.stats.WindowGrid
import com.habitly.app.ui.theme.HabitlyTheme
import com.habitly.app.ui.theme.LocalHabitlyColors
import com.habitly.app.ui.theme.LocalReduceMotion
import com.habitly.app.ui.theme.habitTones
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

private val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")

@Composable
fun WeekReviewScreen(
    uiState: WeekReviewUiState,
    onClose: () -> Unit,
    onApply: (Int) -> Unit,
    onRegenerate: () -> Unit,
    onRetry: () -> Unit,
    onConsentAllowed: () -> Unit,
    onConsentDismissed: () -> Unit,
    weekStart: DayOfWeek,
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            uiState.notEnoughHistory -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
                TopBar("Week in review", pages = 0, current = 0, onClose = onClose, onRegenerate = null)
                EmptyState(
                    icon = Icons.Rounded.AutoAwesome,
                    title = "Not enough history yet",
                    message = "Your first review appears after ${WeekReviewSchedule.MIN_TRACKING_DAYS} days of tracking.",
                    modifier = Modifier.weight(1f),
                )
            }
            uiState.pages.isNotEmpty() -> Story(uiState, onClose, onApply, onRegenerate, weekStart)
            else -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
                TopBar("Week in review", pages = 0, current = 0, onClose = onClose, onRegenerate = null)
                Column(
                    Modifier.weight(1f).padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when {
                        uiState.isLoading -> {
                            AiSkeleton(lines = 4)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Writing your week in review…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        uiState.error != null -> AiErrorCard(uiState.error, onRetry)
                    }
                }
            }
        }
    }
    if (uiState.needsConsent) ConsentSheet(onAllow = onConsentAllowed, onDismiss = onConsentDismissed)
}

@Composable
private fun Story(
    uiState: WeekReviewUiState,
    onClose: () -> Unit,
    onApply: (Int) -> Unit,
    onRegenerate: () -> Unit,
    weekStart: DayOfWeek,
) {
    val pages = uiState.pages
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current
    val goTo: (Int) -> Unit = { target ->
        scope.launch {
            val index = target.coerceIn(0, pages.lastIndex)
            if (reduceMotion) pagerState.scrollToPage(index) else pagerState.animateScrollToPage(index)
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopBar(
            title = "Week in review · ${pagerState.currentPage + 1} of ${pages.size}",
            pages = pages.size,
            current = pagerState.currentPage,
            onClose = onClose,
            onRegenerate = onRegenerate,
        )
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
            PageContent(
                page = pages[index],
                uiState = uiState,
                weekStart = weekStart,
                onApply = { onApply(index) },
                onSkip = { goTo(index + 1) },
                onDone = onClose,
            )
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (pagerState.currentPage > 0) TextButton(onClick = { goTo(pagerState.currentPage - 1) }) { Text("Previous") } else Spacer(Modifier.width(1.dp))
            if (pagerState.currentPage < pages.lastIndex) TextButton(onClick = { goTo(pagerState.currentPage + 1) }) { Text("Next") }
        }
    }
}

@Composable
private fun TopBar(title: String, pages: Int, current: Int, onClose: () -> Unit, onRegenerate: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (pages > 0) {
            val tracks = LocalHabitlyColors.current.gridEmpty
            Row(
                Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "Page ${current + 1} of $pages" },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(pages) { i ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (i <= current) MaterialTheme.colorScheme.onSurface else tracks),
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            if (onRegenerate != null) {
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Regenerate") },
                            onClick = {
                                menuOpen = false
                                onRegenerate()
                            },
                        )
                    }
                }
            }
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close") }
        }
    }
}

private fun eyebrowFor(kind: PageKind) = when (kind) {
    PageKind.SUMMARY -> "This week"
    PageKind.WIN -> "Win"
    PageKind.PATTERN -> "Pattern"
    PageKind.TIP -> "Tip"
    PageKind.CLOSE -> "Next week"
}

@Composable
private fun PageContent(
    page: ReviewPageUi,
    uiState: WeekReviewUiState,
    weekStart: DayOfWeek,
    onApply: () -> Unit,
    onSkip: () -> Unit,
    onDone: () -> Unit,
) {
    val bigText = LocalDensity.current.fontScale > 1.3f
    val eyebrow = eyebrowFor(page.kind)
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        // One screen-reader stop for the words; the visual and the buttons stay separate.
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            Text(
                eyebrow.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = LocalHabitlyColors.current.onSurfaceFaint,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                page.headline,
                style = if (bigText) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Text(page.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
        when (page.kind) {
            PageKind.SUMMARY -> SummaryVisual(uiState, weekStart)
            PageKind.WIN -> page.habit?.let { WinVisual(it) }
            PageKind.PATTERN -> page.habit?.let { PatternVisual(it) }
            PageKind.TIP -> TipActions(page, onApply, onSkip)
            PageKind.CLOSE -> PrimaryButton("Done", onDone, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SummaryVisual(uiState: WeekReviewUiState, weekStart: DayOfWeek) {
    if (uiState.stackDays.isEmpty()) return
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.padding(16.dp)) {
            WindowGrid(
                GridModel(
                    period = StatsPeriod.WEEK,
                    colorHex = null,
                    habitName = null,
                    window = uiState.stackDays,
                    stacks = uiState.stacks,
                    levels = emptyMap(),
                    // A day past the window, so no cell is marked as "today".
                    today = uiState.stackDays.last().plusDays(1),
                    slotCount = uiState.slotCount,
                    habitStart = null,
                ),
                weekStart,
            )
        }
    }
}

@Composable
private fun WinVisual(habit: HabitVisual) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconTile(habit.iconKey, habit.colorHex, size = 64.dp)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(habit.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "${habit.currentStreak}-day streak",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private val DAY_NAMES = DayOfWeek.entries.map { it.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) }

@Composable
private fun PatternVisual(habit: HabitVisual) {
    val tones = habitTones(habit.colorHex)
    val track = LocalHabitlyColors.current.gridEmpty
    val description = habit.weekdayRates.mapIndexed { i, r -> "${DAY_NAMES[i]} ${(r * 100).roundToInt()}%" }.joinToString(", ")
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "${habit.name} by weekday: $description" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        habit.weekdayRates.forEachIndexed { i, rate ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(8.dp)).background(track), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 4.dp)
                            .fillMaxHeight(rate.coerceIn(0f, 1f).coerceAtLeast(0.03f))
                            .clip(RoundedCornerShape(8.dp))
                            .background(tones.full),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(DAY_LETTERS[i], style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun TipActions(page: ReviewPageUi, onApply: () -> Unit, onSkip: () -> Unit) {
    val action = page.action ?: return
    if (page.applied) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Applied ✓", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(action.label, onApply)
            TextButton(onClick = onSkip) { Text("Skip") }
        }
    }
}

// ---- Previews ----

private val previewHabit = HabitVisual(1, "Run", "run", "#FF922B", 9, listOf(0.9f, 0.8f, 0.85f, 0.7f, 0.6f, 0.3f, 0.2f))
private val previewDays = (0L..6L).map { LocalDate.of(2026, 9, 28).plusDays(it) }
private val previewState = WeekReviewUiState(
    weekStart = previewDays.first(), weekLabel = "28 Sep – 4 Oct",
    pages = listOf(
        ReviewPageUi(PageKind.SUMMARY, "Solid week — 82% kept", "You completed 23 of 28 planned check-ins."),
        ReviewPageUi(PageKind.WIN, "Run is on fire", "Nine days straight.", previewHabit),
        ReviewPageUi(PageKind.PATTERN, "Weekends dip", "Saturdays and Sundays slip.", previewHabit),
        ReviewPageUi(PageKind.TIP, "Move the reminder", "An evening reminder fits you better.", previewHabit, ValidAction.SetReminder(1, "Move to 19:00", "19:00")),
        ReviewPageUi(PageKind.CLOSE, "See you next week", "Your next review is ready on Sunday evening."),
    ),
    stackDays = previewDays,
    stacks = previewDays.associateWith { listOf(DaySlice("#FF922B", false, "Run"), DaySlice("#4DABF7", it.dayOfWeek == DayOfWeek.TUESDAY, "Read")) },
    slotCount = 2,
)

@Composable
private fun PreviewContent(state: WeekReviewUiState) {
    WeekReviewScreen(state, {}, {}, {}, {}, {}, {}, DayOfWeek.MONDAY)
}

@Preview(showBackground = true, heightDp = 780) @Composable private fun ReviewSummaryDarkPreview() =
    HabitlyTheme(darkTheme = true) { PreviewContent(previewState) }
@Preview(showBackground = true, heightDp = 780) @Composable private fun ReviewSummaryLightPreview() =
    HabitlyTheme(darkTheme = false) { PreviewContent(previewState) }
@Preview(showBackground = true, heightDp = 780) @Composable private fun ReviewLoadingPreview() =
    HabitlyTheme(darkTheme = true) { PreviewContent(WeekReviewUiState(isLoading = true)) }
@Preview(showBackground = true, heightDp = 780) @Composable private fun ReviewErrorPreview() =
    HabitlyTheme(darkTheme = true) { PreviewContent(WeekReviewUiState(error = AiResult.Offline)) }
