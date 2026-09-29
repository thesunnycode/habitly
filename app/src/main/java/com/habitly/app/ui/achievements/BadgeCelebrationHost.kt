package com.habitly.app.ui.achievements

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.habitly.app.data.prefs.SettingsPrefs
import com.habitly.app.data.repository.AchievementProgress
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.domain.AchievementType
import com.habitly.app.ui.components.Confetti
import com.habitly.app.ui.components.PrimaryButton
import com.habitly.app.ui.theme.HabitlyTheme
import com.habitly.app.ui.theme.LocalReduceMotion
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val SETTLE_MS = 300L

/**
 * Keys to mark seen without a celebration on the very first collection, so someone who already
 * earned badges before this screen existed is not shown a string of dialogs. Badges already earned
 * but not yet stored (progress at target) count too: the next evaluation would otherwise store and
 * celebrate them all at once. Null when this is not a silent first run.
 */
internal fun silentFirstRunKeys(progress: List<AchievementProgress>, seen: Set<String>): Set<String>? {
    if (seen.isNotEmpty()) return null
    val earned = progress.filter { it.unlockedAt != null || it.progress >= it.type.target }.map { it.type.name }.toSet()
    return earned.ifEmpty { null }
}

/** Unlocked badges not yet seen and not already queued, in badge order. */
internal fun newlyUnlocked(
    progress: List<AchievementProgress>,
    seen: Set<String>,
    queued: Set<AchievementType>,
): List<AchievementType> =
    progress.filter { it.unlockedAt != null && it.type.name !in seen && it.type !in queued }.map { it.type }

/**
 * Watches for new badges from anywhere in the app and celebrates each one once, one at a time.
 * Placed once, in the app scaffold.
 */
@OptIn(FlowPreview::class)
@Composable
fun BadgeCelebrationHost(repository: HabitDataSource, prefs: SettingsPrefs) {
    val queue = remember { mutableStateListOf<AchievementType>() }
    // Everything ever queued this session, so a badge is not re-queued while its "seen" write is in flight.
    val handled = remember { mutableSetOf<AchievementType>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(repository, prefs) {
        // Store anything earned before this build (or while nothing evaluated) first, so the tiles
        // show it as unlocked and the first-run check below silences it rather than celebrating it.
        repository.reconcileAchievements()
        var first = true
        combine(repository.observeAchievementProgress(LocalDate.now()), prefs.seenBadgeKeys) { progress, seen -> progress to seen }
            // Let bursts of writes (demo seeding, then its "seen" mark) settle before deciding anything.
            .debounce(SETTLE_MS)
            .collect { (progress, seen) ->
                // Something else (demo seeding) may mark a queued badge seen before it is shown.
                queue.removeAll { it.name in seen }
                if (first) {
                    first = false
                    val silent = silentFirstRunKeys(progress, seen)
                    if (silent != null) {
                        handled += AchievementType.entries.filter { it.name in silent }
                        prefs.markBadgesSeen(silent)
                        return@collect
                    }
                }
                val fresh = newlyUnlocked(progress, seen, handled)
                handled += fresh
                queue += fresh
            }
    }

    val current = queue.firstOrNull() ?: return
    BadgeUnlockDialog(
        type = current,
        onContinue = {
            queue.remove(current)
            scope.launch { prefs.markBadgesSeen(setOf(current.name)) }
        },
    )
}

/** Full-screen unlock moment. Stays until the user taps Continue or presses back. */
@Composable
private fun BadgeUnlockDialog(type: AchievementType, onContinue: () -> Unit) {
    Dialog(
        onDismissRequest = onContinue,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        BadgeUnlockContent(type, onContinue)
    }
}

@Composable
private fun BadgeUnlockContent(type: AchievementType, onContinue: () -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    val scale = remember(type) { Animatable(if (reduceMotion) 1f else 0.4f) }
    LaunchedEffect(type) {
        if (!reduceMotion) scale.animateTo(1f, spring(dampingRatio = 0.45f))
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        if (!reduceMotion) Confetti()
        Surface(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth()
                .semantics {
                    paneTitle = "New badge"
                    liveRegion = LiveRegionMode.Polite
                },
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BadgeCircle(type, unlocked = true, size = 96.dp, modifier = Modifier.scale(scale.value))
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "New badge",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = type.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = type.criterion,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                PrimaryButton(text = "Continue", onClick = onContinue, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Preview @Composable private fun BadgeUnlockDarkPreview() = HabitlyTheme(darkTheme = true) {
    BadgeUnlockContent(AchievementType.STREAK_3, onContinue = {})
}
@Preview @Composable private fun BadgeUnlockLightPreview() = HabitlyTheme(darkTheme = false) {
    BadgeUnlockContent(AchievementType.EARLY_BIRD, onContinue = {})
}
