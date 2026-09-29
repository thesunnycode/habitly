package com.habitly.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.theme.HabitlyTheme

/**
 * The app's one screen header. Deliberately not a [androidx.compose.material3.TopAppBar]: the
 * title sits on the content surface, and a nested TopAppBar would re-apply the status-bar inset
 * that MainActivity's scaffold already consumed.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    eyebrow: String? = null,
    onBack: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)) {
        if (onBack != null || onClose != null || actions != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                } else if (onClose != null) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }
                Spacer(Modifier.weight(1f))
                if (actions != null) Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        }
        if (eyebrow != null) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScreenHeaderPreviewContent() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ScreenHeader(title = "Today", eyebrow = "Monday, 29 September", subtitle = "3 of 5 done")
            ScreenHeader(
                title = "Morning run",
                subtitle = "Fitness",
                onBack = {},
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.Rounded.MoreVert, contentDescription = "More", modifier = Modifier.size(24.dp)) }
                },
            )
            ScreenHeader(title = "New habit", onClose = {})
        }
    }
}

@Preview @Composable private fun ScreenHeaderDarkPreview() = HabitlyTheme(darkTheme = true) { ScreenHeaderPreviewContent() }
@Preview @Composable private fun ScreenHeaderLightPreview() = HabitlyTheme(darkTheme = false) { ScreenHeaderPreviewContent() }
