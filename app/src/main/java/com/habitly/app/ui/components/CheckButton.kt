package com.habitly.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.theme.HabitlyTheme
import com.habitly.app.ui.theme.LocalReduceMotion
import com.habitly.app.ui.theme.habitTones

enum class CheckState { EMPTY, DONE, IN_PROGRESS, SKIPPED }

/** The empty square's hint glyph stays well below the in-progress "+" so the two never look alike. */
private const val EMPTY_GLYPH_ALPHA = 0.45f

/** The tappable completion square: [size] visual inside a 48dp touch target. */
@Composable
fun CheckButton(
    state: CheckState,
    colorHex: String,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    /** A numeric-goal habit: a tap adds one unit, so the empty square hints "+" rather than a tick. */
    countsUp: Boolean = false,
) {
    val tones = habitTones(colorHex)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = if (LocalReduceMotion.current) snap()
        else spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "checkPress",
    )
    val haptic = LocalHapticFeedback.current
    // Haptic only on a real transition into DONE, never for a row that is already DONE when composed.
    var previous by remember { mutableStateOf(state) }
    LaunchedEffect(state) {
        if (state == CheckState.DONE && previous != CheckState.DONE) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        previous = state
    }
    val shape = RoundedCornerShape(12.dp)
    val stateText = when (state) {
        CheckState.DONE -> "Done"
        CheckState.EMPTY -> "Not done"
        CheckState.IN_PROGRESS -> "In progress"
        CheckState.SKIPPED -> "Rest day"
    }
    // Empty is a soft tile in the habit's colour with a faint glyph, so it reads as something to tap;
    // done fills it with the full colour.
    val fill = when (state) {
        CheckState.DONE -> tones.full
        CheckState.EMPTY, CheckState.IN_PROGRESS, CheckState.SKIPPED -> tones.low
    }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Checkbox,
                onClick = onClick,
            )
            .semantics {
                this.contentDescription = contentDescription
                this.stateDescription = stateText
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .scale(scale)
                .clip(shape)
                .background(fill),
            contentAlignment = Alignment.Center,
        ) {
            val glyphSize = Modifier.size(size * 0.6f)
            when (state) {
                CheckState.DONE -> Icon(Icons.Rounded.Check, null, tint = tones.onFull, modifier = glyphSize)
                CheckState.IN_PROGRESS -> Icon(Icons.Rounded.Add, null, tint = tones.full, modifier = glyphSize)
                CheckState.SKIPPED -> Icon(Icons.Rounded.Remove, null, tint = tones.full, modifier = glyphSize)
                CheckState.EMPTY -> Icon(
                    if (countsUp) Icons.Rounded.Add else Icons.Rounded.Check,
                    null,
                    tint = tones.full.copy(alpha = EMPTY_GLYPH_ALPHA),
                    modifier = glyphSize,
                )
            }
        }
    }
}

@Composable
private fun CheckButtonPreviewContent() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CheckState.entries.forEach { CheckButton(it, "#51CF66", {}, "Sample habit") }
            CheckButton(CheckState.EMPTY, "#22B8CF", {}, "Sample goal habit", countsUp = true)
        }
    }
}

@Preview @Composable private fun CheckButtonDarkPreview() = HabitlyTheme(darkTheme = true) { CheckButtonPreviewContent() }
@Preview @Composable private fun CheckButtonLightPreview() = HabitlyTheme(darkTheme = false) { CheckButtonPreviewContent() }
