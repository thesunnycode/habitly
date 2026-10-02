package com.habitly.app.ui.coach

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.habitly.app.ai.AiResult
import com.habitly.app.ai.actions.ValidAction
import com.habitly.app.ai.ui.AiErrorCard
import com.habitly.app.ai.ui.AiRemainingNote
import com.habitly.app.ai.ui.AiSparkleIcon
import com.habitly.app.ai.ui.ConsentSheet
import com.habitly.app.ui.components.HabitlySheet
import com.habitly.app.ui.components.HabitlySheetBody
import com.habitly.app.ui.components.PrimaryButton
import com.habitly.app.ui.components.SecondaryButton
import com.habitly.app.ui.theme.HabitlyTheme
import com.habitly.app.ui.theme.LocalHabitlyColors
import com.habitly.app.ui.theme.LocalReduceMotion

@Composable
fun CoachSheet(
    uiState: CoachUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onChip: (String) -> Unit,
    onAction: (Int, ValidAction) -> Unit,
    onRetry: () -> Unit,
    onConsentAllowed: () -> Unit,
    onConsentDismissed: () -> Unit,
    onDismiss: () -> Unit,
) {
    HabitlySheet(onDismiss = onDismiss, title = "Quick coach", skipPartiallyExpanded = false) {
        CoachSheetContent(uiState, onInputChange, onSend, onChip, onAction, onRetry)
    }
    if (uiState.needsConsent) ConsentSheet(onAllow = onConsentAllowed, onDismiss = onConsentDismissed)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoachSheetContent(
    uiState: CoachUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onChip: (String) -> Unit,
    onAction: (Int, ValidAction) -> Unit,
    onRetry: () -> Unit,
) {
    val listState = rememberLazyListState()
    val rows = uiState.messages.size + if (uiState.isTyping) 1 else 0
    LaunchedEffect(rows) {
        if (rows > 0) listState.animateScrollToItem(rows - 1)
    }
    Column(Modifier.imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(uiState.messages, key = { it.id }) { message ->
                when (message.role) {
                    CoachRole.COACH -> CoachBubble(message, onAction)
                    CoachRole.USER -> UserBubble(message)
                }
            }
            if (uiState.isTyping) item(key = "typing") { TypingIndicator() }
        }
        uiState.error?.let { AiErrorCard(it, onRetry) }
        AiRemainingNote(uiState.remaining)
        if (!uiState.isTyping) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(COACH_CHIPS) { chip ->
                    AssistChip(
                        onClick = { onChip(chip) },
                        label = { Text(chip) },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = uiState.input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask anything…") },
                maxLines = 3,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, capitalization = KeyboardCapitalization.Sentences),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onSend, enabled = uiState.canSend) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoachBubble(message: CoachMessage, onAction: (Int, ValidAction) -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    // Starts hidden and flips on first composition so each new reply fades in; skipped under reduce motion.
    var visible by remember { mutableStateOf(reduceMotion) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(visible, enter = if (reduceMotion) EnterTransition.None else fadeIn()) {
        Column(
            Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(LocalHabitlyColors.current.surfaceRaised),
                    contentAlignment = Alignment.Center,
                ) { AiSparkleIcon(Modifier.size(16.dp)) }
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.widthIn(max = 320.dp),
                ) {
                    Text(
                        message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
            if (message.actions.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(start = 36.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    message.actions.forEachIndexed { index, action ->
                        if (index == 0) PrimaryButton(action.label, { onAction(message.id, action) })
                        else SecondaryButton(action.label, { onAction(message.id, action) })
                    }
                }
            }
            message.appliedNote?.let { note ->
                Row(Modifier.padding(start = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun UserBubble(message: CoachMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

/** Three dots pulsing in sequence; static under reduce motion. */
@Composable
private fun TypingIndicator() {
    val reduceMotion = LocalReduceMotion.current
    val transition = rememberInfiniteTransition(label = "coachTyping")
    Row(
        modifier = Modifier
            .padding(start = 36.dp)
            .semantics { contentDescription = "Coach is typing" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(3) { index ->
            val alpha = if (reduceMotion) {
                0.6f
            } else {
                val pulse by transition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 0.3f,
                    animationSpec = infiniteRepeatable(
                        animation = keyframes {
                            durationMillis = 1200
                            0.3f at index * 200
                            1f at index * 200 + 300
                            0.3f at index * 200 + 600
                        },
                        repeatMode = RepeatMode.Restart,
                    ),
                    label = "dot$index",
                )
                pulse
            }
            Box(
                Modifier
                    .size(8.dp)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
    }
}

// ---- Previews ----

private val previewMessages = listOf(
    CoachMessage(
        0, CoachRole.COACH,
        "You have 2 habits left. Workout is at risk — a 15-min version still keeps your streak.",
        listOf(ValidAction.LogDone(1, "Log workout"), ValidAction.OpenHabit(1, "Open habit")),
    ),
    CoachMessage(1, CoachRole.USER, "Why do I slip?"),
)

@Composable
private fun CoachPreviewContent(state: CoachUiState) {
    Box(Modifier.background(LocalHabitlyColors.current.surfaceRaised)) {
        HabitlySheetBody(title = "Quick coach") {
            CoachSheetContent(state, {}, {}, {}, { _, _ -> }, {})
        }
    }
}

@Preview @Composable private fun CoachOpeningDarkPreview() =
    HabitlyTheme(darkTheme = true) { CoachPreviewContent(CoachUiState(messages = previewMessages.take(1))) }
@Preview @Composable private fun CoachOpeningLightPreview() =
    HabitlyTheme(darkTheme = false) { CoachPreviewContent(CoachUiState(messages = previewMessages.take(1))) }
@Preview @Composable private fun CoachTypingPreview() =
    HabitlyTheme(darkTheme = true) { CoachPreviewContent(CoachUiState(messages = previewMessages, isTyping = true, input = "hm")) }
@Preview @Composable private fun CoachErrorPreview() =
    HabitlyTheme(darkTheme = true) { CoachPreviewContent(CoachUiState(messages = previewMessages, error = AiResult.Offline)) }
