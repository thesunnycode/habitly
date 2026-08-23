package com.habitly.app.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.habitly.app.data.prefs.OnboardingPrefs

/**
 * Asks for notification permission once the user has actually logged something.
 *
 * Asking during onboarding is one of the largest drop-off points in a wellness app, and at that
 * moment a reminder has nothing to offer — there is no habit to be reminded about yet. After the
 * first check-in the request explains itself.
 */
@Composable
fun NotificationPermissionPrompt(hasLoggedSomething: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val prefs = remember { OnboardingPrefs(context) }
    // Defaults to true so a slow first read cannot fire the prompt spuriously.
    val alreadyAsked by prefs.hasShownNotificationPrompt.collectAsState(initial = true)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(hasLoggedSomething, alreadyAsked) {
        if (hasLoggedSomething && !alreadyAsked) {
            // Recorded before launching, so a declined dialog is not re-asked on every check-in.
            prefs.setNotificationPromptShown()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
