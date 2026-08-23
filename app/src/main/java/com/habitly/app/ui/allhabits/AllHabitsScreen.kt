package com.habitly.app.ui.allhabits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.components.HabitCard

@Composable
fun AllHabitsScreen(
    uiState: AllHabitsUiState,
    onSearchQueryChange: (String) -> Unit,
    onCategorySelect: (String?) -> Unit,
    onHabitClick: (Long) -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("All Habits") }) }) { padding ->
        Column(modifier = Modifier.fillMaxWidth().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChange,
                label = { Text("Search habits") },
                modifier = Modifier.fillMaxWidth(),
            )
            LazyRow(modifier = Modifier.padding(vertical = 8.dp)) {
                items(uiState.categories) { category ->
                    FilterChip(
                        selected = uiState.selectedCategory == category,
                        onClick = { onCategorySelect(category) },
                        label = { Text(category) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(uiState.filteredHabits, key = { it.habit.id }) { habit ->
                    HabitCard(habit = habit, onToggle = {}, onClick = { onHabitClick(habit.habit.id) })
                }
            }
        }
    }
}
