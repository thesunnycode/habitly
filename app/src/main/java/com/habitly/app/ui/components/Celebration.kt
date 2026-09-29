package com.habitly.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.theme.HabitPalette
import com.habitly.app.ui.theme.HabitlyTheme
import com.habitly.app.ui.theme.LocalReduceMotion
import com.habitly.app.ui.theme.hexToColor
import kotlin.random.Random
import kotlinx.coroutines.delay

private const val CONFETTI_COUNT = 60
private const val CELEBRATION_MS = 2500

private class Particle(
    val x: Float,
    val phase: Float,
    val speed: Float,
    val width: Float,
    val height: Float,
    val spin: Float,
    val color: Color,
)

/** Full-screen "you did it" moment: scrim, a centred card and confetti. Dismisses itself after 2.5s. */
@Composable
fun Celebration(
    visible: Boolean,
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(visible) {
        delay(CELEBRATION_MS.toLong())
        currentOnDismiss()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (!LocalReduceMotion.current) Confetti()
        Surface(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Celebration,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                PrimaryButton(text = "Nice", onClick = onDismiss)
            }
        }
    }
}

@Composable
internal fun Confetti() {
    val particles = remember {
        val random = Random(7)
        val colors = HabitPalette.seeds.map { hexToColor(it) }
        List(CONFETTI_COUNT) {
            Particle(
                x = random.nextFloat(),
                phase = random.nextFloat(),
                speed = 0.6f + random.nextFloat() * 0.6f,
                width = 6f + random.nextFloat() * 6f,
                height = 10f + random.nextFloat() * 8f,
                spin = random.nextFloat() * 720f,
                color = colors[it % colors.size],
            )
        }
    }
    val progress by rememberInfiniteTransition(label = "confetti").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(CELEBRATION_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "confettiProgress",
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val density = this.density
        particles.forEach { p ->
            val fall = (progress * p.speed + p.phase) % 1f
            val w = p.width * density
            val h = p.height * density
            val cx = p.x * size.width
            val cy = fall * (size.height + h) - h
            rotate(degrees = p.spin * progress, pivot = Offset(cx, cy)) {
                drawRoundRect(
                    color = p.color,
                    topLeft = Offset(cx - w / 2, cy - h / 2),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(2f * density),
                )
            }
        }
    }
}

@Preview
@Composable
private fun CelebrationDarkPreview() = HabitlyTheme(darkTheme = true) {
    Celebration(visible = true, title = "7 day streak", message = "You are on a roll.", onDismiss = {})
}

@Preview
@Composable
private fun CelebrationLightPreview() = HabitlyTheme(darkTheme = false) {
    Celebration(visible = true, title = "7 day streak", message = "You are on a roll.", onDismiss = {})
}
