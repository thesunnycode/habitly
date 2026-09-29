package com.habitly.app.ui.insights

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.components.SegmentedPill

/** Temporary host: Stats / Calendar under a pill until the real Insights screen lands. */
@Composable
fun InsightsScreen(
    tab: String,
    onTabChange: (String) -> Unit,
    statsContent: @Composable () -> Unit,
    calendarContent: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        SegmentedPill(
            options = listOf("stats", "calendar"),
            selected = tab,
            onSelect = onTabChange,
            label = { it.replaceFirstChar { c -> c.uppercase() } },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Box(Modifier.weight(1f)) {
            if (tab == "calendar") calendarContent() else statsContent()
        }
    }
}
