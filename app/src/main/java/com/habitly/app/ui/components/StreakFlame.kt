package com.habitly.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.theme.StreakFlame as StreakFlameColor

@Composable
fun StreakFlame(streak: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        Icon(
            imageVector = Icons.Filled.LocalFireDepartment,
            contentDescription = "Current streak",
            tint = if (streak > 0) StreakFlameColor else MaterialTheme.colorScheme.outline,
        )
        Text(
            text = "$streak",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
