package com.habitly.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.habitly.app.ui.theme.habitTones

enum class CheckState { EMPTY, DONE, IN_PROGRESS, SKIPPED }

/** The tappable completion square: [size] visual inside a 48dp touch target. */
@Composable
fun CheckButton(
    state: CheckState,
    colorHex: String,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val tones = habitTones(colorHex)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "checkPress",
    )
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(state) {
        if (state == CheckState.DONE) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val shape = RoundedCornerShape(12.dp)
    val stateText = when (state) {
        CheckState.DONE -> "Done"
        CheckState.EMPTY -> "Not done"
        CheckState.IN_PROGRESS -> "In progress"
        CheckState.SKIPPED -> "Rest day"
    }
    val fill = when (state) {
        CheckState.EMPTY -> null
        CheckState.DONE -> tones.full
        CheckState.IN_PROGRESS, CheckState.SKIPPED -> tones.low
    }
    val outline = MaterialTheme.colorScheme.outline
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
                .then(
                    if (fill == null) Modifier.border(BorderStroke(1.5.dp, outline), shape)
                    else Modifier.background(fill),
                ),
            contentAlignment = Alignment.Center,
        ) {
            val glyphSize = Modifier.size(size * 0.6f)
            when (state) {
                CheckState.DONE -> Icon(Icons.Rounded.Check, null, tint = tones.onFull, modifier = glyphSize)
                CheckState.IN_PROGRESS -> Icon(Icons.Rounded.Add, null, tint = tones.full, modifier = glyphSize)
                CheckState.SKIPPED -> Icon(Icons.Rounded.Remove, null, tint = tones.full, modifier = glyphSize)
                CheckState.EMPTY -> Unit
            }
        }
    }
}

@Composable
private fun CheckButtonPreviewContent() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CheckState.entries.forEach { CheckButton(it, "#51CF66", {}, "Sample habit") }
        }
    }
}

@Preview @Composable private fun CheckButtonDarkPreview() = HabitlyTheme(darkTheme = true) { CheckButtonPreviewContent() }
@Preview @Composable private fun CheckButtonLightPreview() = HabitlyTheme(darkTheme = false) { CheckButtonPreviewContent() }
