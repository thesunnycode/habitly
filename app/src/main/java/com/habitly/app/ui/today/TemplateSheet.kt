package com.habitly.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.habitly.app.domain.HabitSchedule
import com.habitly.app.domain.HabitTemplate
import com.habitly.app.domain.HabitTemplateGroup
import com.habitly.app.domain.HabitTemplates
import com.habitly.app.domain.label
import com.habitly.app.ui.components.HabitlySheet
import com.habitly.app.ui.components.HabitlySheetBody
import com.habitly.app.ui.components.SectionLabel
import com.habitly.app.ui.components.habitIconFor
import com.habitly.app.ui.theme.HabitlyTheme
import com.habitly.app.ui.theme.LocalHabitlyColors
import com.habitly.app.ui.theme.habitTones

/** "New habit" picker: start from scratch, or from one of the grouped templates. `null` means scratch. */
@Composable
fun TemplateSheet(onDismiss: () -> Unit, onPick: (HabitTemplate?) -> Unit) {
    HabitlySheet(onDismiss = onDismiss, title = "New habit") {
        TemplateSheetContent(onPick)
    }
}

@Composable
private fun TemplateSheetContent(onPick: (HabitTemplate?) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val groups = filterTemplates(HabitTemplates.groups, query)
    Column(Modifier.verticalScroll(rememberScrollState())) {
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search templates") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        Spacer(Modifier.height(12.dp))
        ScratchTile(onClick = { onPick(null) })
        groups.forEach { group ->
            Spacer(Modifier.height(20.dp))
            SectionLabel(group.title)
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                group.templates.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { template ->
                            TemplateTile(template, onClick = { onPick(template) }, modifier = Modifier.weight(1f))
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        if (groups.isEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "No templates match. Start from scratch instead.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Groups keeping only templates whose name contains [query]; empty groups are dropped. */
internal fun filterTemplates(groups: List<HabitTemplateGroup>, query: String): List<HabitTemplateGroup> {
    val q = query.trim()
    if (q.isEmpty()) return groups
    return groups
        .map { group -> group.copy(templates = group.templates.filter { it.name.contains(q, ignoreCase = true) }) }
        .filter { it.templates.isNotEmpty() }
}

/** "8 cups · daily", "daily", "Sun". */
internal fun templateSubline(template: HabitTemplate): String {
    val schedule = HabitSchedule(template.frequencyType, template.specificDays).label()
    val scheduleText = if (schedule == "Daily") "daily" else schedule
    val goal = template.targetCount?.let { count -> listOfNotNull("$count", template.unit).joinToString(" ") }
    return listOfNotNull(goal, scheduleText).joinToString(" · ")
}

@Composable
private fun ScratchTile(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(LocalHabitlyColors.current.surfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Start from scratch", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "Name it, pick a colour and a schedule",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TemplateTile(template: HabitTemplate, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tones = habitTones(template.colorHex)
    Column(
        modifier = modifier
            .heightIn(min = 104.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(tones.low)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
    ) {
        Icon(
            habitIconFor(template.icon),
            contentDescription = null,
            tint = tones.full,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = template.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = templateSubline(template),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TemplateSheetPreviewContent() {
    Box(Modifier.background(LocalHabitlyColors.current.surfaceRaised)) {
        HabitlySheetBody(title = "New habit") { TemplateSheetContent(onPick = {}) }
    }
}

@Preview @Composable private fun TemplateSheetDarkPreview() = HabitlyTheme(darkTheme = true) { TemplateSheetPreviewContent() }
@Preview @Composable private fun TemplateSheetLightPreview() = HabitlyTheme(darkTheme = false) { TemplateSheetPreviewContent() }
