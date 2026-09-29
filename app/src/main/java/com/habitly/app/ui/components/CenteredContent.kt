package com.habitly.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Widest a single-column screen gets; past this, cards and calendar cells just grow without reading better. */
val MAX_CONTENT_WIDTH = 640.dp

/**
 * Centres a single-column screen and caps it at [MAX_CONTENT_WIDTH] on tablets and landscape. Phones
 * are narrower than the cap, so nothing changes there. [enabled] = false passes content through, for
 * screens that switch to their own two-pane layout at expanded widths.
 */
@Composable
fun CenteredContent(enabled: Boolean = true, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.fillMaxHeight().widthIn(max = MAX_CONTENT_WIDTH).fillMaxWidth()) { content() }
    }
}
