package com.habitly.app.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.theme.LocalHabitlyColors
import java.util.Locale

/**
 * The themed reminder picker, shared by the habit form and the Reminders screen: a full-height
 * sheet with the primary-coloured dial, "Turn off" and "Set". [onSet] receives "HH:mm".
 */
@Composable
internal fun ReminderTimeSheet(
    time: String?,
    onSet: (String) -> Unit,
    onTurnOff: () -> Unit,
    onDismiss: () -> Unit,
    title: String = "Reminder",
) {
    val context = LocalContext.current
    val (initialHour, initialMinute) = remember(time) {
        time?.split(":")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 2 }
            ?.let { it[0] to it[1] } ?: (9 to 0)
    }
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    val scheme = MaterialTheme.colorScheme
    val extras = LocalHabitlyColors.current
    HabitlySheet(onDismiss = onDismiss, title = title, skipPartiallyExpanded = true) {
        // Scrolls on short screens so the actions below the dial are always reachable.
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = scheme.surface,
                        clockDialSelectedContentColor = scheme.onPrimary,
                        clockDialUnselectedContentColor = scheme.onSurface,
                        selectorColor = scheme.primary,
                        periodSelectorBorderColor = scheme.outline,
                        periodSelectorSelectedContainerColor = scheme.primaryContainer,
                        periodSelectorUnselectedContainerColor = extras.surfaceRaised,
                        periodSelectorSelectedContentColor = scheme.onPrimaryContainer,
                        periodSelectorUnselectedContentColor = scheme.onSurfaceVariant,
                        timeSelectorSelectedContainerColor = scheme.primaryContainer,
                        timeSelectorUnselectedContainerColor = scheme.surface,
                        timeSelectorSelectedContentColor = scheme.onPrimaryContainer,
                        timeSelectorUnselectedContentColor = scheme.onSurface,
                    ),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton(
                    text = "Turn off",
                    onClick = onTurnOff,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = "Set",
                    onClick = { onSet("%02d:%02d".format(Locale.ROOT, state.hour, state.minute)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
