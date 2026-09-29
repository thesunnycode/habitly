package com.habitly.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.theme.LocalHabitlyColors

/** Confirmation as a bottom sheet: icon, explanation, a stacked confirm and cancel. */
@Composable
fun ConfirmSheet(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    icon: ImageVector = Icons.Rounded.DeleteOutline,
    destructive: Boolean = true,
) {
    val extras = LocalHabitlyColors.current
    val accent = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    HabitlySheet(onDismiss = onDismiss) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton(
            text = confirmLabel,
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            containerColor = if (destructive) MaterialTheme.colorScheme.error else extras.primaryButton,
            contentColor = if (destructive) MaterialTheme.colorScheme.onError else extras.onPrimaryButton,
        )
        Spacer(Modifier.height(8.dp))
        SecondaryButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}
