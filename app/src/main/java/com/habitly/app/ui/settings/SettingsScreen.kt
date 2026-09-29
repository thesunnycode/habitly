package com.habitly.app.ui.settings

import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.habitly.app.data.prefs.ThemeMode
import com.habitly.app.ui.components.ConfirmDialog
import com.habitly.app.ui.components.ScreenHeader
import com.habitly.app.ui.components.SectionCard

private fun labelFor(mode: ThemeMode) = when (mode) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onThemeModeChange: (ThemeMode) -> Unit,
    onNotificationsToggle: (Boolean) -> Unit,
    onResetAllData: () -> Unit,
    onBack: () -> Unit = {},
) {
    var showResetConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().systemBarsPadding(), // Temporary until this screen is rebuilt: the shell no longer pads for system bars.
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader(title = "Settings", subtitle = "Habitly preferences", onBack = onBack) }

        item {
            SectionCard(title = "Appearance", subtitle = "Choose how Habitly looks") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = uiState.themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            label = { Text(labelFor(mode)) },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = "Reminders") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.padding(end = 12.dp)) {
                        Text("Reminder notifications", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "Nudges at each habit's reminder time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = uiState.notificationsEnabled,
                        onCheckedChange = onNotificationsToggle,
                    )
                }
            }
        }

        item {
            SectionCard(title = "Data", subtitle = "Everything is stored on this device only") {
                OutlinedButton(
                    onClick = { showResetConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Reset all data", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showResetConfirm) {
        ConfirmDialog(
            title = "Reset all data?",
            message = "This deletes every habit and all history. This cannot be undone.",
            confirmLabel = "Reset",
            onConfirm = { showResetConfirm = false; onResetAllData() },
            onDismiss = { showResetConfirm = false },
        )
    }
}
