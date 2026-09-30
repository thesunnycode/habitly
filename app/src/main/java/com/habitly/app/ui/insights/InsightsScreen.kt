package com.habitly.app.ui.insights

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.components.ScreenHeader
import com.habitly.app.ui.components.SegmentedPill

const val INSIGHTS_STATS = "stats"
const val INSIGHTS_CALENDAR = "calendar"

/**
 * The Insights tab: Stats and Calendar behind a pill. On an expanded width both fit, so they sit
 * side by side and the pill goes away.
 */
@Composable
fun InsightsScreen(
    tab: String,
    onTabChange: (String) -> Unit,
    stats: @Composable () -> Unit,
    calendar: @Composable () -> Unit,
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    /** Sits at the right of the title — the Stats period switch. Only shown while Stats is visible. */
    statsTitleTrailing: (@Composable () -> Unit)? = null,
) {
    val expanded = windowWidthSizeClass == WindowWidthSizeClass.Expanded
    val trailing = statsTitleTrailing?.takeIf { expanded || tab != INSIGHTS_CALENDAR }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(
            title = "Insights",
            modifier = Modifier.padding(horizontal = 20.dp),
            titleTrailing = trailing,
        )
        if (expanded) {
            Row(Modifier.weight(1f)) {
                Box(Modifier.weight(0.55f)) { stats() }
                Box(Modifier.weight(0.45f)) { calendar() }
            }
        } else {
            SegmentedPill(
                options = listOf(INSIGHTS_STATS, INSIGHTS_CALENDAR),
                selected = tab,
                onSelect = onTabChange,
                label = { if (it == INSIGHTS_CALENDAR) "Calendar" else "Stats" },
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            )
            Box(Modifier.weight(1f)) {
                if (tab == INSIGHTS_CALENDAR) calendar() else stats()
            }
        }
    }
}
