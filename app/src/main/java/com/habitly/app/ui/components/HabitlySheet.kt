package com.habitly.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.habitly.app.ui.theme.HabitlyTheme
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.theme.LocalHabitlyColors

/** The one bottom-sheet container: raised surface, 28dp top corners, standard padding and insets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitlySheet(
    onDismiss: () -> Unit,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LocalHabitlyColors.current.surfaceRaised,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) },
    ) {
        HabitlySheetBody(title, content)
    }
}

/** The padded content column inside [HabitlySheet]; separate so it can be previewed. */
@Composable
internal fun HabitlySheetBody(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
            .navigationBarsPadding(),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))
        }
        content()
    }
}

@Composable
private fun HabitlySheetPreviewContent() {
    Box(Modifier.background(LocalHabitlyColors.current.surfaceRaised)) {
        HabitlySheetBody(title = "Sort habits") {
            Text("Sheet content goes here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview @Composable private fun HabitlySheetDarkPreview() = HabitlyTheme(darkTheme = true) { HabitlySheetPreviewContent() }
@Preview @Composable private fun HabitlySheetLightPreview() = HabitlyTheme(darkTheme = false) { HabitlySheetPreviewContent() }
