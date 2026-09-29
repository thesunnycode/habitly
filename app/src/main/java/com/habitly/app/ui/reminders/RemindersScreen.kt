package com.habitly.app.ui.reminders

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.prefs.OnboardingPrefs
import com.habitly.app.domain.FrequencyType
import com.habitly.app.ui.components.EmptyState
import com.habitly.app.ui.components.IconTile
import com.habitly.app.ui.components.PrimaryButton
import com.habitly.app.ui.components.ReminderBellTile
import com.habitly.app.ui.components.REMINDER_EXPLAINER
import com.habitly.app.ui.components.ReminderTimeSheet
import com.habitly.app.ui.components.ScreenHeader
import com.habitly.app.ui.components.SettingsGroup
import com.habitly.app.ui.components.SettingsRow
import com.habitly.app.ui.theme.HabitPalette
import com.habitly.app.ui.theme.HabitlyTheme
import kotlinx.coroutines.launch

@Composable
fun RemindersScreen(
    uiState: RemindersUiState,
    onSetReminder: (Long, String?) -> Unit,
    onBack: () -> Unit,
) {
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }

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
        ScreenHeader(title = "Reminders", onBack = onBack)
        NotificationPermissionCard()
        if (uiState.habits.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.NotificationsNone,
                title = "No habits yet",
                message = "Add a habit, then choose when to be reminded about it.",
            )
        } else {
            SettingsGroup(title = "Habit reminders") {
                uiState.habits.forEachIndexed { index, habit ->
                    SettingsRow(
                        title = habit.name,
                        leading = { IconTile(habit.icon, habit.colorHex, size = 32.dp) },
                        value = habit.reminderTime ?: "Off",
                        onClick = { editingId = habit.id },
                        showDivider = index > 0,
                    )
                }
            }
        }
    }

    val editing = editingId?.let { id -> uiState.habits.firstOrNull { it.id == id } }
    if (editing != null) {
        ReminderTimeSheet(
            time = editing.reminderTime,
            title = editing.name,
            onSet = { onSetReminder(editing.id, it); editingId = null },
            onTurnOff = { onSetReminder(editing.id, null); editingId = null },
            onDismiss = { editingId = null },
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Shown only while notifications are blocked. Asks through the system dialog; once Android stops
 * offering it (a second denial, reported as no rationale after an earlier request), sends the user
 * to the app's notification settings instead.
 */
@Composable
private fun NotificationPermissionCard() {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val prefs = remember { OnboardingPrefs(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val requestedBefore by prefs.hasRequestedNotificationPermission.collectAsState(initial = false)

    // Bumped on resume and after a request, so the permission state is re-read from the system.
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    val enabled = remember(refresh) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    if (enabled) return

    val canAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val permanentlyDenied = remember(refresh, requestedBefore) {
        !canAsk || (requestedBefore && activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == false)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        ReminderBellTile()
        Spacer(Modifier.height(12.dp))
        Text(
            text = REMINDER_EXPLAINER,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))
        if (permanentlyDenied) {
            PrimaryButton(
                text = "Open system settings",
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            PrimaryButton(
                text = "Allow notifications",
                onClick = {
                    scope.launch { prefs.setNotificationPermissionRequested() }
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun previewHabit(id: Long, name: String, icon: String, color: String, time: String?) = HabitEntity(
    id, name, icon, color, "Health", FrequencyType.DAILY, null, null, time, 0L, false,
)

@Composable
private fun RemindersPreviewContent() {
    Box(Modifier.background(MaterialTheme.colorScheme.background)) {
        RemindersScreen(
            uiState = RemindersUiState(
                listOf(
                    previewHabit(1, "Drink water", "water_drop", HabitPalette.seeds[1], "08:00"),
                    previewHabit(2, "Read", "menu_book", HabitPalette.seeds[2], null),
                ),
            ),
            onSetReminder = { _, _ -> },
            onBack = {},
        )
    }
}

@Preview @Composable private fun RemindersDarkPreview() = HabitlyTheme(darkTheme = true) { RemindersPreviewContent() }
@Preview @Composable private fun RemindersLightPreview() = HabitlyTheme(darkTheme = false) { RemindersPreviewContent() }
