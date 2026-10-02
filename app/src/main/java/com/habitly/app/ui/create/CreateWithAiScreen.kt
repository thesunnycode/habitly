package com.habitly.app.ui.create

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.habitly.app.ai.AiResult
import com.habitly.app.ai.ui.AiErrorCard
import com.habitly.app.ai.ui.AiRemainingNote
import com.habitly.app.ai.ui.AiSkeleton
import com.habitly.app.ai.ui.ConsentSheet
import com.habitly.app.domain.FrequencyType
import com.habitly.app.domain.HabitSchedule
import com.habitly.app.domain.HabitTemplate
import com.habitly.app.domain.label
import com.habitly.app.ui.components.IconTile
import com.habitly.app.ui.components.PrimaryButton
import com.habitly.app.ui.components.ScreenHeader
import com.habitly.app.ui.components.SecondaryButton
import com.habitly.app.ui.components.SectionLabel
import com.habitly.app.ui.theme.HabitlyTheme
import com.habitly.app.ui.theme.LocalHabitlyColors
import com.habitly.app.ui.theme.LocalReduceMotion
import java.time.DayOfWeek

private const val COUNTER_THRESHOLD = 250

/** hero, input, chips, suggest: the results label is the item right after these. */
private const val RESULTS_ITEM_INDEX_HINT = 4

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateWithAiScreen(
    uiState: CreateWithAiUiState,
    onTextChange: (String) -> Unit,
    onChip: (String) -> Unit,
    onSuggest: () -> Unit,
    onTryAgain: () -> Unit,
    onAdd: (Int) -> Unit,
    onUndo: (Int) -> Unit,
    onAddAll: () -> Unit,
    onDismiss: (Int) -> Unit,
    onEdit: (Int) -> Unit,
    onConsentAllowed: () -> Unit,
    onConsentDismissed: () -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberLazyListState()
    // Results land below the fold on a phone; bring them into view when they arrive.
    val hasResults = uiState.visibleCards.isNotEmpty()
    LaunchedEffect(hasResults) {
        if (hasResults) listState.animateScrollToItem(RESULTS_ITEM_INDEX_HINT.coerceAtMost(listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(
            title = "Create with AI",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "hero") {
                Column {
                    Text(
                        "What do you want to change?",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Say it in your own words",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "input") { PromptCard(uiState.text, onTextChange) }
            item(key = "chips") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PROMPT_CHIPS.forEach { chip ->
                        AssistChip(
                            onClick = { onChip(chip) },
                            label = { Text(chip) },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        )
                    }
                }
            }
            item(key = "suggest") {
                Column {
                    PrimaryButton(
                        text = "Suggest",
                        onClick = onSuggest,
                        enabled = uiState.canSuggest,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = Icons.Rounded.AutoAwesome,
                    )
                    AiRemainingNote(uiState.remaining, Modifier.padding(top = 8.dp))
                }
            }
            if (uiState.isLoading) {
                items(2, key = { "skeleton$it" }) { SkeletonCard() }
            }
            uiState.error?.let { error -> item(key = "error") { AiErrorCard(error, onTryAgain) } }
            uiState.emptyMessage?.let { message ->
                item(key = "empty") { MessageCard(message, onTryAgain) }
            }
            if (uiState.visibleCards.isNotEmpty()) {
                item(key = "results-label") { ResultsLabel(uiState) }
                items(uiState.visibleCards, key = { "card${it.key}" }) { card ->
                    SuggestionCardView(
                        card = card,
                        onAdd = { onAdd(card.key) },
                        onEdit = { onEdit(card.key) },
                        onDismiss = { onDismiss(card.key) },
                        onUndo = { onUndo(card.key) },
                    )
                }
            }
            if (uiState.hasNew) {
                item(key = "footer") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SecondaryButton("Add all", onAddAll)
                        TextButton(onClick = onTryAgain) { Text("Try again") }
                    }
                }
            }
        }
    }
    if (uiState.needsConsent) ConsentSheet(onAllow = onConsentAllowed, onDismiss = onConsentDismissed)
}

@Composable
private fun PromptCard(text: String, onTextChange: (String) -> Unit) {
    val context = LocalContext.current
    val canSpeak = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(context.packageManager) != null
    }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onTextChange)
        }
    }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                placeholder = { Text("I want to sleep before midnight…") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (text.length > COUNTER_THRESHOLD) {
                    Text(
                        "${text.length}/$MAX_PROMPT_CHARS",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (canSpeak) {
                    IconButton(onClick = {
                        speech.launch(
                            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                .putExtra(RecognizerIntent.EXTRA_PROMPT, "What do you want to change?"),
                        )
                    }) {
                        Icon(Icons.Rounded.Mic, contentDescription = "Speak instead of typing")
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonCard() {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) { AiSkeleton(lines = 3) }
    }
}

@Composable
private fun MessageCard(message: String, onRetry: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))
            SecondaryButton("Try again", onRetry)
        }
    }
}

@Composable
private fun ResultsLabel(uiState: CreateWithAiUiState) {
    val count = uiState.visibleCards.size
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("AI suggests $count ${if (count == 1) "habit" else "habits"}", modifier = Modifier.weight(1f, fill = false))
        uiState.intent?.let { intent ->
            Surface(shape = RoundedCornerShape(50), color = LocalHabitlyColors.current.surfaceRaised) {
                Text(
                    intent.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** "Mon, Fri · 8 cups · 09:00" — only the parts the suggestion actually has. */
internal fun suggestionSubline(template: HabitTemplate): String {
    val schedule = HabitSchedule(template.frequencyType, template.specificDays, template.intervalDays).label()
    val goal = template.targetCount?.let { listOfNotNull("$it", template.unit).joinToString(" ") }
    return listOfNotNull(schedule, goal, template.reminderTime).joinToString(" · ")
}

@Composable
private fun SuggestionCardView(
    card: SuggestionCard,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
    onUndo: () -> Unit,
) {
    val template = card.suggestion.template
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(template.icon, template.colorHex, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(template.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        suggestionSubline(template),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (card.suggestion.why.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    card.suggestion.why,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            val transition: ContentTransform = if (LocalReduceMotion.current) {
                fadeIn(snap()) togetherWith fadeOut(snap())
            } else {
                fadeIn() togetherWith fadeOut()
            }
            AnimatedContent(
                targetState = card.status,
                transitionSpec = { transition },
                label = "suggestionActions",
            ) { status ->
                if (status == CardStatus.ADDED) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Added", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onUndo) { Text("Undo") }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PrimaryButton("Add", onAdd)
                        SecondaryButton("Edit", onEdit)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Dismiss ${template.name}")
                        }
                    }
                }
            }
        }
    }
}

// ---- Previews ----

private val previewCards = listOf(
    SuggestionCard(
        0,
        Suggestion(
            HabitTemplate("Wind down", "sleep", "#748FFC", "Health", reminderTime = "22:15"),
            "A fixed routine tells your body when sleep is coming.",
        ),
    ),
    SuggestionCard(
        1,
        Suggestion(
            HabitTemplate(
                "No caffeine after 2pm", "water_drop", "#22B8CF", "Health",
                frequencyType = FrequencyType.SPECIFIC_DAYS, specificDays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                targetCount = 3, unit = "cups",
            ),
            "Caffeine lingers for hours.",
        ),
        status = CardStatus.ADDED,
        habitId = 5,
    ),
)

@Composable
private fun PreviewContent(state: CreateWithAiUiState) {
    Surface(color = MaterialTheme.colorScheme.background) {
        CreateWithAiScreen(state, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview @Composable private fun CreateEmptyPreview() = HabitlyTheme(darkTheme = true) { PreviewContent(CreateWithAiUiState()) }
@Preview @Composable private fun CreateLoadingPreview() =
    HabitlyTheme(darkTheme = true) { PreviewContent(CreateWithAiUiState(text = "sleep earlier", isLoading = true)) }
@Preview @Composable private fun CreateResultsDarkPreview() =
    HabitlyTheme(darkTheme = true) { PreviewContent(CreateWithAiUiState(text = "sleep earlier", intent = "sleep", cards = previewCards, remaining = 2)) }
@Preview @Composable private fun CreateResultsLightPreview() =
    HabitlyTheme(darkTheme = false) { PreviewContent(CreateWithAiUiState(text = "sleep earlier", intent = "sleep", cards = previewCards)) }
@Preview @Composable private fun CreateErrorPreview() =
    HabitlyTheme(darkTheme = true) { PreviewContent(CreateWithAiUiState(text = "sleep earlier", error = AiResult.Offline)) }
