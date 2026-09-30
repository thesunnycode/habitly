package com.habitly.app.ui.addedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.components.HabitlySheet
import com.habitly.app.ui.components.PrimaryButton
import com.habitly.app.ui.components.SectionLabel

private val SUGGESTED_UNITS = listOf("times", "cups", "glasses", "pages", "minutes", "km", "steps")
private const val MIN_GOAL = 1
private const val MAX_GOAL = 99

/** Sets how many units make a full day. Nothing is applied until "Set goal". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalSheet(
    targetCount: Int?,
    unit: String,
    onSet: (target: Int, unit: String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var count by rememberSaveable { mutableIntStateOf((targetCount ?: 2).coerceIn(MIN_GOAL, MAX_GOAL)) }
    var unitText by rememberSaveable { mutableStateOf(unit.ifBlank { "times" }) }

    HabitlySheet(onDismiss = onDismiss, title = "Daily goal", skipPartiallyExpanded = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Stepper(
                value = count,
                onValueChange = { count = it },
                range = MIN_GOAL..MAX_GOAL,
                valueDescription = { "$it $unitText" },
            )
        }
        Spacer(Modifier.height(16.dp))
        TextField(
            value = unitText,
            onValueChange = { unitText = it.take(20) },
            label = { Text("Unit") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            shape = RoundedCornerShape(16.dp),
            colors = formFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        SectionLabel("Suggestions")
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SUGGESTED_UNITS.forEach { suggestion ->
                FilterChip(
                    selected = unitText.trim() == suggestion,
                    onClick = { unitText = suggestion },
                    label = { Text(suggestion) },
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = "Set goal",
            onClick = { onSet(count, unitText.trim()) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (targetCount != null) {
            TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("Remove goal", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
