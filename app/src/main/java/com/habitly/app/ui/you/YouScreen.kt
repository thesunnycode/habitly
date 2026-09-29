package com.habitly.app.ui.you

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.components.ScreenHeader
import com.habitly.app.ui.components.SettingsGroup
import com.habitly.app.ui.components.SettingsRow

/** Temporary body: entry points only, replaced by the real You screen later. */
@Composable
fun YouScreen(onAwards: () -> Unit, onArchived: () -> Unit, onSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        ScreenHeader(title = "You")
        SettingsGroup {
            SettingsRow(title = "Awards", icon = Icons.Rounded.EmojiEvents, onClick = onAwards)
            SettingsRow(title = "Archived", icon = Icons.Rounded.Archive, onClick = onArchived, showDivider = true)
            SettingsRow(title = "Settings", icon = Icons.Rounded.Settings, onClick = onSettings, showDivider = true)
        }
    }
}
