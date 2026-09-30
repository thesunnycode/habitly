package com.habitly.app.ui.addedit

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.components.HABIT_ICON_KEYS
import com.habitly.app.ui.components.HabitlySheet
import com.habitly.app.ui.components.IconTile

/** Every habit icon as a 6-column grid of tiles in the habit's colour; picking one closes the sheet. */
@Composable
fun IconPickerSheet(
    selectedIcon: String,
    colorHex: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    HabitlySheet(onDismiss = onDismiss, title = "Choose an icon", skipPartiallyExpanded = true) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(HABIT_ICON_KEYS) { key ->
                val selected = key == selectedIcon
                val shape = RoundedCornerShape(16.dp)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(shape)
                        .border(2.dp, if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, shape)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(key) })
                        .semantics { contentDescription = key.replace('_', ' ') },
                    contentAlignment = Alignment.Center,
                ) {
                    IconTile(iconKey = key, colorHex = colorHex, size = 40.dp)
                }
            }
        }
    }
}
