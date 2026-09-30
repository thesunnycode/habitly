package com.habitly.app.ui.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.habitly.app.data.export.CsvExporter
import com.habitly.app.ai.ui.ConsentSheet
import com.habitly.app.data.prefs.ThemeMode
import com.habitly.app.ui.components.ConfirmSheet
import com.habitly.app.ui.components.HabitlySheet
import com.habitly.app.ui.components.LocalSnackbarHostState
import com.habitly.app.ui.components.ScreenHeader
import com.habitly.app.ui.components.SegmentedPill
import com.habitly.app.ui.components.SettingsGroup
import com.habitly.app.ui.components.SettingsRow
import com.habitly.app.ui.theme.HabitlyTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

private const val LICENCE_ASSET = "licenses/OFL-PlusJakartaSans.txt"

private fun themeLabel(mode: ThemeMode) = when (mode) {
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.SYSTEM -> "Auto"
}

private fun dayLabel(day: DayOfWeek) = if (day == DayOfWeek.SUNDAY) "Sunday" else "Monday"

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onThemeModeChange: (ThemeMode) -> Unit,
    onWeekStartChange: (DayOfWeek) -> Unit,
    onNotificationsToggle: (Boolean) -> Unit,
    onStreakNudgeToggle: (Boolean) -> Unit,
    onHabitReminders: () -> Unit,
    onExport: suspend () -> String,
    onLoadDemoData: () -> Unit,
    onResetAllData: () -> Unit,
    onBack: () -> Unit,
    onResetMessageShown: () -> Unit = {},
    onAiEnabledToggle: (Boolean) -> Unit = {},
    onShowForecastToggle: (Boolean) -> Unit = {},
    onDemoAiToggle: (Boolean) -> Unit = {},
    onAiHeaderTapped: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarHostState.current
    var showResetConfirm by rememberSaveable { mutableStateOf(false) }
    var showLicences by rememberSaveable { mutableStateOf(false) }
    var showShared by rememberSaveable { mutableStateOf(false) }
    var weekMenuOpen by remember { mutableStateOf(false) }
    val error = MaterialTheme.colorScheme.error

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ScreenHeader(title = "Settings", onBack = onBack)

        SettingsGroup(title = "Appearance") {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                Text("Theme", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(10.dp))
                SegmentedPill(
                    options = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM),
                    selected = uiState.themeMode,
                    onSelect = onThemeModeChange,
                    label = ::themeLabel,
                )
            }
            Box {
                SettingsRow(
                    title = "Week starts",
                    icon = Icons.Rounded.CalendarMonth,
                    value = dayLabel(uiState.weekStart),
                    onClick = { weekMenuOpen = true },
                    showDivider = true,
                )
                DropdownMenu(expanded = weekMenuOpen, onDismissRequest = { weekMenuOpen = false }) {
                    listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY).forEach { day ->
                        DropdownMenuItem(
                            text = { Text(dayLabel(day)) },
                            onClick = { weekMenuOpen = false; onWeekStartChange(day) },
                        )
                    }
                }
            }
        }

        SettingsGroup(title = "Reminders") {
            SettingsRow(
                title = "Notifications",
                icon = Icons.Rounded.Notifications,
                trailing = { HabitlySwitch(uiState.notificationsEnabled, onNotificationsToggle) },
            )
            SettingsRow(
                title = "Streak-at-risk nudge",
                icon = Icons.Rounded.LocalFireDepartment,
                supporting = "A second nudge when a streak of 2+ days is about to break",
                trailing = {
                    HabitlySwitch(uiState.streakNudgeEnabled, onStreakNudgeToggle, enabled = uiState.notificationsEnabled)
                },
                showDivider = true,
            )
            SettingsRow(
                title = "Habit reminders",
                icon = Icons.Rounded.Alarm,
                onClick = onHabitReminders,
                showDivider = true,
            )
        }

        SettingsGroup(title = "AI features", onTitleClick = onAiHeaderTapped) {
            SettingsRow(
                title = "AI features",
                icon = Icons.Rounded.AutoAwesome,
                supporting = "Create with AI, coach and weekly review",
                trailing = { HabitlySwitch(uiState.aiEnabled, onAiEnabledToggle) },
            )
            SettingsRow(
                title = "Show forecast",
                icon = Icons.Rounded.Insights,
                supporting = "Today's on-device risk forecast",
                trailing = { HabitlySwitch(uiState.showForecast, onShowForecastToggle) },
                showDivider = true,
            )
            SettingsRow(
                title = "What's shared",
                icon = Icons.Rounded.Shield,
                onClick = { showShared = true },
                showDivider = true,
            )
            if (uiState.demoAiRevealed) {
                SettingsRow(
                    title = "Demo AI",
                    icon = Icons.Rounded.Science,
                    supporting = "Canned answers for offline demos",
                    trailing = { HabitlySwitch(uiState.demoAi, onDemoAiToggle) },
                    showDivider = true,
                )
            }
        }

        SettingsGroup(title = "Data") {
            SettingsRow(
                title = "Export CSV",
                icon = Icons.Rounded.Share,
                supporting = "Every habit and check-in as a spreadsheet",
                onClick = {
                    scope.launch {
                        try {
                            shareCsv(context, onExport())
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            snackbar.showSnackbar("Couldn't export your data")
                        }
                    }
                },
            )
            if (uiState.isDebug) {
                SettingsRow(
                    title = "Load demo data",
                    icon = Icons.Rounded.Science,
                    supporting = "Adds sample habits when there are none",
                    onClick = onLoadDemoData,
                    showDivider = true,
                )
            }
            SettingsRow(
                title = "Reset all data",
                icon = Icons.Rounded.DeleteForever,
                iconTint = error,
                titleColor = error,
                onClick = { showResetConfirm = true },
                showDivider = true,
            )
        }

        SettingsGroup(title = "About") {
            SettingsRow(
                title = "Version",
                icon = Icons.Rounded.Info,
                supporting = "All data stays on this device",
                value = uiState.versionName,
            )
            SettingsRow(
                title = "Licences",
                icon = Icons.Rounded.Description,
                onClick = { showLicences = true },
                showDivider = true,
            )
        }
    }

    // Announced only once the reset has actually finished, not when it was asked for.
    LaunchedEffect(uiState.resetCompleted) {
        if (uiState.resetCompleted) {
            onResetMessageShown()
            // Outside this effect, so consuming the flag doesn't cancel the snackbar it triggers.
            scope.launch { snackbar.showSnackbar("Everything has been reset") }
        }
    }

    if (showResetConfirm) {
        ConfirmSheet(
            title = "Reset everything?",
            message = "This deletes every habit, all history and your awards. This can't be undone.",
            confirmLabel = "Reset",
            onConfirm = {
                showResetConfirm = false
                onResetAllData()
            },
            onDismiss = { showResetConfirm = false },
        )
    }

    if (showLicences) LicencesSheet(onDismiss = { showLicences = false })

    if (showShared) ConsentSheet(onAllow = {}, onDismiss = { showShared = false }, readOnly = true)
}

/** Switch in the brand colours: `primary` track, `onPrimary` thumb when on. */
@Composable
private fun HabitlySwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@Composable
private fun LicencesSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text by produceState<String?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open(LICENCE_ASSET).bufferedReader().use { it.readText() } }.getOrNull()
        } ?: "The licence text could not be loaded."
    }
    HabitlySheet(onDismiss = onDismiss, title = "Licences", skipPartiallyExpanded = true) {
        Text(
            text = "Plus Jakarta Sans — SIL Open Font License 1.1",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = text.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Writes the CSV to `cache/exports` off the main thread and opens the share sheet for it. */
private suspend fun shareCsv(context: Context, csv: String) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        File(dir, CsvExporter.fileName(LocalDate.now())).apply { writeText(csv) }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/csv")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Export habits"))
}

@Composable
private fun SettingsPreviewContent() {
    Box(Modifier.background(MaterialTheme.colorScheme.background)) {
        SettingsScreen(
            uiState = SettingsUiState(versionName = "1.0", isDebug = true, demoAiRevealed = true),
            onThemeModeChange = {}, onWeekStartChange = {}, onNotificationsToggle = {}, onStreakNudgeToggle = {},
            onHabitReminders = {}, onExport = { "" }, onLoadDemoData = {}, onResetAllData = {}, onBack = {},
        )
    }
}

@Preview @Composable private fun SettingsDarkPreview() = HabitlyTheme(darkTheme = true) { SettingsPreviewContent() }
@Preview @Composable private fun SettingsLightPreview() = HabitlyTheme(darkTheme = false) { SettingsPreviewContent() }
