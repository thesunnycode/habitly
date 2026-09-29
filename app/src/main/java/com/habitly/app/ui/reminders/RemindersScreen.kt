package com.habitly.app.ui.reminders

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.components.ScreenHeader

/** Temporary body: header only. */
@Composable
fun RemindersScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        ScreenHeader(title = "Reminders", onBack = onBack)
    }
}
