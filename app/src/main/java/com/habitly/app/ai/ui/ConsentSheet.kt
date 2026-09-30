package com.habitly.app.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.components.HabitlySheet
import com.habitly.app.ui.components.HabitlySheetBody
import com.habitly.app.ui.components.PrimaryButton
import com.habitly.app.ui.components.SecondaryButton
import com.habitly.app.ui.theme.HabitlyTheme
import com.habitly.app.ui.theme.LocalHabitlyColors

const val CONSENT_TEXT_SENT = "Habit names, schedules, reminder times and daily done / rest / missed counts."
const val CONSENT_TEXT_NOT_SENT = "Your name and your notes."
const val CONSENT_TEXT_WHERE = "Habitly's server, which asks OpenAI and returns suggestions. Nothing is stored there."

private const val CONSENT_TITLE = "Before you use AI"

/**
 * What the AI features share, asked once before the first AI call. [readOnly] shows the same facts from
 * Settings with only a "Close" button.
 */
@Composable
fun ConsentSheet(onAllow: () -> Unit, onDismiss: () -> Unit, readOnly: Boolean = false) {
    HabitlySheet(onDismiss = onDismiss, title = CONSENT_TITLE, skipPartiallyExpanded = true) {
        ConsentSheetContent(onAllow, onDismiss, readOnly)
    }
}

/** The body of [ConsentSheet]; separate so it can be previewed. */
@Composable
internal fun ConsentSheetContent(onAllow: () -> Unit, onDismiss: () -> Unit, readOnly: Boolean) {
    // Scrolls at large font sizes so Allow and Not now are always reachable (the sheet itself clips).
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ConsentRow(Icons.Rounded.Upload, "What's sent", CONSENT_TEXT_SENT)
            ConsentRow(Icons.Rounded.Lock, "Never sent", CONSENT_TEXT_NOT_SENT)
            ConsentRow(Icons.Rounded.Cloud, "Where it goes", CONSENT_TEXT_WHERE)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Today's forecast never leaves your phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        if (readOnly) {
            SecondaryButton(text = "Close", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        } else {
            PrimaryButton(text = "Allow", onClick = onAllow, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            SecondaryButton(text = "Not now", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ConsentRow(icon: ImageVector, heading: String, body: String) {
    // One focus stop per row, so a screen reader reads the heading and its detail together.
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text = heading, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ConsentSheetPreviewContent(readOnly: Boolean) {
    Box(Modifier.background(LocalHabitlyColors.current.surfaceRaised)) {
        HabitlySheetBody(title = CONSENT_TITLE) {
            ConsentSheetContent(onAllow = {}, onDismiss = {}, readOnly = readOnly)
        }
    }
}

@Preview @Composable private fun ConsentSheetDarkPreview() = HabitlyTheme(darkTheme = true) { ConsentSheetPreviewContent(false) }
@Preview @Composable private fun ConsentSheetLightPreview() = HabitlyTheme(darkTheme = false) { ConsentSheetPreviewContent(false) }
@Preview @Composable private fun ConsentSheetReadOnlyDarkPreview() = HabitlyTheme(darkTheme = true) { ConsentSheetPreviewContent(true) }
