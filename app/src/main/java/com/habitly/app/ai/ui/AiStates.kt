package com.habitly.app.ai.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.habitly.app.ai.AiResult
import com.habitly.app.ui.components.SecondaryButton
import com.habitly.app.ui.theme.HabitlyTheme
import com.habitly.app.ui.theme.LocalHabitlyColors
import com.habitly.app.ui.theme.LocalReduceMotion

/** The user-facing copy for an AI failure; null when there is nothing to report (success, or consent pending). */
fun aiErrorMessage(result: AiResult<*>): String? = when (result) {
    is AiResult.Success, AiResult.ConsentRequired -> null
    AiResult.Offline -> "AI needs an internet connection"
    is AiResult.RateLimited -> "Daily AI limit reached — back tomorrow"
    AiResult.NotConfigured -> "AI isn't set up in this build — turn on Demo AI in Settings"
    AiResult.Disabled -> "AI features are turned off in Settings"
    is AiResult.Failed -> "Something went wrong. Try again."
}

private fun errorIcon(result: AiResult<*>): ImageVector = when (result) {
    AiResult.Offline -> Icons.Rounded.CloudOff
    is AiResult.RateLimited -> Icons.Rounded.HourglassEmpty
    AiResult.NotConfigured, AiResult.Disabled -> Icons.Rounded.Settings
    else -> Icons.Rounded.ErrorOutline
}

/** Retrying cannot fix a switched-off, unconfigured or rate-limited AI, so those get no button. */
internal fun canRetry(result: AiResult<*>): Boolean =
    result !is AiResult.RateLimited && result != AiResult.Disabled && result != AiResult.NotConfigured

/** An AI failure as a card: icon, the spec's copy, and "Try again" when retrying can help. */
@Composable
fun AiErrorCard(result: AiResult<*>, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val message = aiErrorMessage(result) ?: return
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    errorIcon(result),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (canRetry(result)) {
                Spacer(Modifier.height(12.dp))
                SecondaryButton(text = "Try again", onClick = onRetry, leadingIcon = Icons.Rounded.Refresh)
            }
        }
    }
}

private val skeletonWidths = listOf(1f, 0.85f, 0.6f)

/** Pulsing placeholder bars while an AI response loads; still when reduce motion is on. */
@Composable
fun AiSkeleton(lines: Int = 3, modifier: Modifier = Modifier) {
    val alpha = if (LocalReduceMotion.current) {
        0.6f
    } else {
        val transition = rememberInfiniteTransition(label = "aiSkeleton")
        val pulse by transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 800), RepeatMode.Reverse),
            label = "aiSkeletonAlpha",
        )
        pulse
    }
    val color = LocalHabitlyColors.current.gridEmpty
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Loading AI response" },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(lines) { index ->
            Box(
                Modifier
                    .fillMaxWidth(skeletonWidths[index % skeletonWidths.size])
                    .height(14.dp)
                    .alpha(alpha)
                    .clip(RoundedCornerShape(7.dp))
                    .background(color),
            )
        }
    }
}

/** A quiet warning once the day's AI allowance is nearly used up; nothing otherwise. */
@Composable
fun AiRemainingNote(remaining: Int?, modifier: Modifier = Modifier) {
    if (remaining == null || remaining > 3) return
    Text(
        text = if (remaining == 1) "1 AI request left today" else "$remaining AI requests left today",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** The sparkle that marks AI-powered UI. Decorative: pair it with a text label. */
@Composable
fun AiSparkleIcon(modifier: Modifier = Modifier) {
    Icon(
        Icons.Rounded.AutoAwesome,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

@Composable
private fun AiStatesPreviewContent() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AiSparkleIcon(Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Create with AI", color = MaterialTheme.colorScheme.onSurface)
            }
            AiSkeleton()
            AiErrorCard(AiResult.Offline, onRetry = {})
            AiErrorCard(AiResult.RateLimited(null), onRetry = {})
            AiRemainingNote(remaining = 2)
            AiRemainingNote(remaining = 1)
        }
    }
}

@Preview @Composable private fun AiStatesDarkPreview() = HabitlyTheme(darkTheme = true) { AiStatesPreviewContent() }
@Preview @Composable private fun AiStatesLightPreview() = HabitlyTheme(darkTheme = false) { AiStatesPreviewContent() }
