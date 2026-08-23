package com.habitly.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.habitly.app.data.prefs.ThemeMode
import com.habitly.app.ui.components.ConfirmDialog

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onThemeModeChange: (ThemeMode) -> Unit,
    onNotificationsToggle: (Boolean) -> Unit,
    onResetAllData: () -> Unit,
) {
    var showResetConfirm by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(modifier = Modifier.fillMaxWidth().padding(padding).padding(16.dp)) {
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = uiState.themeMode == mode,
                        onClick = { onThemeModeChange(mode) },
                        label = { Text(mode.name) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Reminder notifications", style = MaterialTheme.typography.titleMedium)
                Switch(checked = uiState.notificationsEnabled, onCheckedChange = onNotificationsToggle)
            }

            Button(
                onClick = { showResetConfirm = true },
                modifier = Modifier.padding(top = 32.dp),
            ) { Text("Reset All Data") }
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
