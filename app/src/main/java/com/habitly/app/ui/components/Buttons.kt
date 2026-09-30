package com.habitly.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.theme.HabitlyTheme
import com.habitly.app.ui.theme.LocalHabitlyColors

/** The one filled call-to-action: full-pill, 52dp tall. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = LocalHabitlyColors.current.primaryButton,
    contentColor: Color = LocalHabitlyColors.current.onPrimaryButton,
    leadingIcon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.3f),
            disabledContentColor = contentColor.copy(alpha = 0.6f),
        ),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        ButtonContent(text, leadingIcon)
    }
}

/** Quiet pill on the raised surface, for secondary actions. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = LocalHabitlyColors.current.surfaceRaised,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        ButtonContent(text, leadingIcon)
    }
}

@Composable
private fun ButtonContent(text: String, leadingIcon: ImageVector?) {
    if (leadingIcon != null) {
        Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(20.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
    }
    Text(text = text, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun ButtonsPreviewContent() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton("Save habit", {}, Modifier.fillMaxWidth())
            PrimaryButton("New habit", {}, Modifier.fillMaxWidth(), leadingIcon = Icons.Rounded.Add)
            PrimaryButton("Disabled", {}, Modifier.fillMaxWidth(), enabled = false)
            SecondaryButton("Cancel", {}, Modifier.fillMaxWidth())
        }
    }
}

@Preview @Composable private fun ButtonsDarkPreview() = HabitlyTheme(darkTheme = true) { ButtonsPreviewContent() }
@Preview @Composable private fun ButtonsLightPreview() = HabitlyTheme(darkTheme = false) { ButtonsPreviewContent() }
