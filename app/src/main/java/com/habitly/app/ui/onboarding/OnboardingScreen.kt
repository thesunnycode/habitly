package com.habitly.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.habitly.app.domain.HabitTemplate
import com.habitly.app.domain.HabitTemplates
import com.habitly.app.ui.addedit.formFieldColors
import com.habitly.app.ui.components.IconTile
import com.habitly.app.ui.components.PrimaryButton
import com.habitly.app.ui.theme.HabitlyTheme
import com.habitly.app.ui.theme.LocalHabitlyColors
import com.habitly.app.ui.theme.LocalReduceMotion
import com.habitly.app.ui.theme.habitTones

private const val STEP_COUNT = 4
private const val WELCOME_WEEKS = 12
private const val WELCOME_FILL_MS = 1600

/** Four steps: welcome grid, forgiving streaks, an optional name, then starter habits. */
@Composable
fun OnboardingScreen(
    uiState: OnboardingUiState,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onToggleTemplate: (String) -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val focusManager = LocalFocusManager.current
    val templates = remember { ONBOARDING_TEMPLATE_KEYS.mapNotNull(HabitTemplates::byKey) }
    val step = uiState.step

    BackHandler(enabled = step > 0) { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            // The name step's Continue button stays above the keyboard.
            .imePadding()
            .padding(horizontal = 24.dp),
    ) {
        TopRow(step = step, onSkip = onSkip)

        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                if (reduceMotion) {
                    fadeIn() togetherWith fadeOut()
                } else {
                    val forward = targetState > initialState
                    (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                        (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
                }
            },
            label = "onboardingStep",
        ) { shown ->
            when (shown) {
                0 -> WelcomeStep()
                1 -> ForgivingStep()
                2 -> NameStep(
                    name = uiState.name,
                    onNameChange = onNameChange,
                    onDone = { focusManager.clearFocus(); onNext() },
                )
                else -> PickStep(templates = templates, selected = uiState.selected, onToggle = onToggleTemplate)
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (step) {
                0, 1 -> PrimaryButton(text = "Next", onClick = onNext, modifier = Modifier.fillMaxWidth())
                2 -> PrimaryButton(
                    text = "Continue",
                    onClick = { focusManager.clearFocus(); onNext() },
                    modifier = Modifier.fillMaxWidth(),
                )
                else -> {
                    val count = uiState.selected.size
                    PrimaryButton(
                        text = if (count == 1) "Add 1 habit" else "Add $count habits",
                        onClick = onFinish,
                        enabled = count > 0,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = onSkip, modifier = Modifier.padding(top = 4.dp)) {
                        Text("Start with no habits")
                    }
                }
            }
        }
    }
}

@Composable
private fun TopRow(step: Int, onSkip: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProgressDashes(step = step, modifier = Modifier.weight(1f))
        // Hidden on the last step, which has its own "Start with no habits".
        if (step < STEP_COUNT - 1) {
            TextButton(onClick = onSkip, modifier = Modifier.padding(start = 8.dp)) { Text("Skip") }
        }
    }
}

@Composable
private fun ProgressDashes(step: Int, modifier: Modifier = Modifier) {
    val reduceMotion = LocalReduceMotion.current
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = "Step ${step + 1} of $STEP_COUNT" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(STEP_COUNT) { index ->
            val width by animateDpAsState(
                targetValue = if (index == step) 28.dp else 20.dp,
                animationSpec = if (reduceMotion) snap() else tween(250),
                label = "dashWidth",
            )
            Box(
                modifier = Modifier
                    .width(width)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index <= step) scheme.onSurface else scheme.outline),
            )
        }
    }
}

@Composable
private fun StepText(headline: String, body: String?) {
    Text(
        text = headline,
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() },
    )
    if (body != null) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WelcomeStep() {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        FillingGrid(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(40.dp))
        StepText(
            headline = "Small days\nadd up",
            body = "Tick off a habit, watch your grid fill in. That's the whole idea.",
        )
    }
}

/** A made-up but steady-looking history: denser towards the right, like a habit taking hold. */
private fun welcomeLevel(column: Int, row: Int): Int {
    val noise = ((column * 7 + row) * 2654435761L % 97).toInt() // deterministic scatter, 0..96
    val bias = column * 60 / (WELCOME_WEEKS - 1) // 0 at the left, 60 at the right
    val score = noise + bias
    return when {
        score < 45 -> 0
        score < 75 -> 1
        score < 100 -> 2
        score < 125 -> 3
        else -> 4
    }
}

/** 12 weeks × 7 days in the brand tones, filling in left to right (static under reduce motion). */
@Composable
private fun FillingGrid(modifier: Modifier = Modifier) {
    val reduceMotion = LocalReduceMotion.current
    val progress = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) progress.animateTo(1f, tween(WELCOME_FILL_MS, easing = LinearEasing))
    }
    val scheme = MaterialTheme.colorScheme
    val empty = LocalHabitlyColors.current.gridEmpty
    val palette = remember(scheme.primary, scheme.primaryContainer, empty) {
        // Same brand ramp as HabitGrid with no habit colour.
        val mid = lerp(scheme.primaryContainer, scheme.primary, 0.55f)
        listOf(
            empty,
            lerp(scheme.primaryContainer, scheme.primary, 0.30f),
            mid,
            lerp(mid, scheme.primary, 0.5f),
            scheme.primary,
        )
    }
    val gapDp = 4.dp
    Canvas(
        modifier = modifier
            .aspectRatio(WELCOME_WEEKS / 7f)
            .clearAndSetSemantics { },
    ) {
        val gap = gapDp.toPx()
        val cell = minOf((size.width - gap * (WELCOME_WEEKS - 1)) / WELCOME_WEEKS, (size.height - gap * 6) / 7)
        val startX = (size.width - (cell * WELCOME_WEEKS + gap * (WELCOME_WEEKS - 1))) / 2
        val radius = CornerRadius(cell * 0.22f)
        val filledColumns = progress.value * WELCOME_WEEKS
        for (c in 0 until WELCOME_WEEKS) {
            for (r in 0 until 7) {
                // Within a column, days fill top to bottom as the sweep passes.
                val filled = c + (r + 1) / 7f <= filledColumns + 1e-3f
                val level = if (filled) welcomeLevel(c, r) else 0
                drawRoundRect(
                    color = palette[level],
                    topLeft = Offset(startX + c * (cell + gap), r * (cell + gap)),
                    size = Size(cell, cell),
                    cornerRadius = radius,
                )
            }
        }
    }
}

private enum class IllustrationDay { DONE, REST, TODAY }

@Composable
private fun ForgivingStep() {
    val days = listOf(
        IllustrationDay.DONE, IllustrationDay.DONE, IllustrationDay.REST, IllustrationDay.DONE,
        IllustrationDay.DONE, IllustrationDay.DONE, IllustrationDay.TODAY,
    )
    val scheme = MaterialTheme.colorScheme
    val restFill = lerp(scheme.primaryContainer, scheme.primary, 0.30f)
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    contentDescription = "Five done days and one rest day, and the streak carries on to today"
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            days.forEach { day ->
                val shape = RoundedCornerShape(10.dp)
                val base = Modifier.weight(1f).aspectRatio(1f).clip(shape)
                Box(
                    modifier = when (day) {
                        IllustrationDay.DONE -> base.background(scheme.primary)
                        IllustrationDay.REST -> base.background(restFill)
                        IllustrationDay.TODAY -> base.border(2.dp, scheme.onSurface, shape)
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    when (day) {
                        IllustrationDay.DONE ->
                            Icon(Icons.Rounded.Check, null, tint = scheme.onPrimary, modifier = Modifier.size(18.dp))
                        IllustrationDay.REST ->
                            Icon(Icons.Rounded.Remove, null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                        IllustrationDay.TODAY -> Unit
                    }
                }
            }
        }
        Spacer(Modifier.height(40.dp))
        StepText(
            headline = "Streaks that\nforgive",
            body = "Missed a day for a good reason? Mark it as rest and your streak carries on.",
        )
    }
}

@Composable
private fun NameStep(name: String, onNameChange: (String) -> Unit, onDone: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        StepText(headline = "What should we\ncall you?", body = null)
        Spacer(Modifier.height(24.dp))
        TextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Your name") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = formFieldColors(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Optional — used for your greeting.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
private fun PickStep(templates: List<HabitTemplate>, selected: Set<String>, onToggle: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(16.dp))
        StepText(headline = "Pick a few habits\nto start", body = null)
        Spacer(Modifier.height(20.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(templates, key = { it.key }) { template ->
                TemplateTile(
                    template = template,
                    selected = template.key in selected,
                    onToggle = { onToggle(template.key) },
                )
            }
        }
    }
}

@Composable
private fun TemplateTile(template: HabitTemplate, selected: Boolean, onToggle: () -> Unit) {
    val tones = habitTones(template.colorHex)
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) tones.low else MaterialTheme.colorScheme.surface)
            .then(if (selected) Modifier.border(2.dp, tones.full, shape) else Modifier)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() }),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            IconTile(iconKey = template.icon, colorHex = template.colorHex, size = 40.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = template.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(tones.full),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = tones.onFull, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun OnboardingPreviewContent(step: Int) {
    OnboardingScreen(
        uiState = OnboardingUiState(step = step, selected = setOf("drink_water", "meditate")),
        onNext = {}, onBack = {}, onNameChange = {}, onToggleTemplate = {}, onSkip = {}, onFinish = {},
    )
}

@Preview @Composable private fun OnboardingWelcomeDarkPreview() = HabitlyTheme(darkTheme = true) { OnboardingPreviewContent(0) }
@Preview @Composable private fun OnboardingForgivingDarkPreview() = HabitlyTheme(darkTheme = true) { OnboardingPreviewContent(1) }
@Preview @Composable private fun OnboardingNameLightPreview() = HabitlyTheme(darkTheme = false) { OnboardingPreviewContent(2) }
@Preview @Composable private fun OnboardingPickDarkPreview() = HabitlyTheme(darkTheme = true) { OnboardingPreviewContent(3) }
