package com.habitly.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.habitly.app.data.prefs.OnboardingPrefs
import kotlinx.coroutines.delay

/** Longer than the badge host's settle time plus an evaluation, so a fresh badge wins the race. */
private const val PROMPT_SETTLE_MS = 1_000L

/** The one explainer line for reminders, shared by this sheet and the Reminders permission card. */
internal const val REMINDER_EXPLAINER = "Get a nudge at the time you chose — only if the habit isn't done yet."

/**
 * Offers reminders once the user has actually logged something, as a sheet leading to the
 * Reminders screen (which owns the system permission request).
 *
 * Asking during onboarding is one of the largest drop-off points in a wellness app, and at that
 * moment a reminder has nothing to offer — there is no habit to be reminded about yet. After the
 * first check-in the offer explains itself.
 */
@Composable
fun NotificationPermissionPrompt(
    hasLoggedSomething: Boolean,
    onTurnOnReminders: () -> Unit,
    /** A celebration is on screen; the offer waits until it is dismissed rather than stacking on it. */
    blocked: Boolean = false,
) {
    val context = LocalContext.current.applicationContext
    val prefs = remember { OnboardingPrefs(context) }
    // Defaults to true so a slow first read cannot show the sheet spuriously.
    val alreadyShown by prefs.hasShownNotificationPrompt.collectAsState(initial = true)
    var showing by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(hasLoggedSomething, alreadyShown, blocked) {
        if (hasLoggedSomething && !alreadyShown && !blocked) {
            // The same check-in may earn a badge a moment later; let it queue (which sets blocked and
            // restarts this effect) before offering anything.
            delay(PROMPT_SETTLE_MS)
            // Recorded as it opens, so a dismissed sheet is not offered again on every check-in.
            showing = true
            prefs.setNotificationPromptShown()
        }
    }

    if (showing) {
        HabitlySheet(onDismiss = { showing = false }) {
            ReminderBellTile()
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Want a reminder?",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = REMINDER_EXPLAINER,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                text = "Turn on reminders",
                onClick = { showing = false; onTurnOnReminders() },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton(text = "Not now", onClick = { showing = false }, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** The bell on its brand tile that heads every reminders explainer. */
@Composable
internal fun ReminderBellTile() {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.NotificationsActive,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
    }
}
