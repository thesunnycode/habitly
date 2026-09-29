package com.habitly.app.ui.allhabits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.habitly.app.data.repository.HabitWithStats
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.ui.components.HabitCard
import com.habitly.app.ui.components.ScreenHeader

@Composable
fun AllHabitsScreen(
    uiState: AllHabitsUiState,
    onSearchQueryChange: (String) -> Unit,
    onCategorySelect: (String?) -> Unit,
    onToggleHabit: (Long) -> Unit,
    onHabitClick: (Long) -> Unit,
    onAddHabit: () -> Unit = {},
    onSetStatus: (Long, CompletionStatus?) -> Unit = { _, _ -> },
) {
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddHabit,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New habit") },
            )
        },
    ) { padding ->
        // Only the bottom inset is taken from this Scaffold: the outer HabitlyScaffold already
        // applied the status-bar inset, and consuming it twice leaves a dead band above the title.
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(bottom = padding.calculateBottomPadding()), // Temporary until the Habits rebuild.
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { AllHabitsHeader(uiState) }
            item { TodayProgress(uiState) }
            item {
                HabitSearchField(
                    query = uiState.searchQuery,
                    onQueryChange = onSearchQueryChange,
                )
            }
            item {
                CategoryFilterRow(
                    categories = uiState.categories,
                    counts = uiState.categoryCounts,
                    selected = uiState.selectedCategory,
                    totalCount = uiState.totalToday,
                    onCategorySelect = onCategorySelect,
                )
            }

            if (uiState.filteredHabits.isEmpty()) {
                item { NoMatchesMessage(uiState) }
            }

            if (uiState.todoHabits.isNotEmpty()) {
                item { SectionLabel("TO DO", uiState.todoHabits.size) }
                habitItems(uiState.todoHabits, onToggleHabit, onHabitClick, onSetStatus)
            }

            if (uiState.completedHabits.isNotEmpty()) {
                item { SectionLabel("DONE", uiState.completedHabits.size) }
                habitItems(uiState.completedHabits, onToggleHabit, onHabitClick, onSetStatus)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.habitItems(
    habits: List<HabitWithStats>,
    onToggleHabit: (Long) -> Unit,
    onHabitClick: (Long) -> Unit,
    onSetStatus: (Long, CompletionStatus?) -> Unit,
) {
    items(habits, key = { it.habit.id }) { habit ->
        HabitCard(
            habit = habit,
            onToggle = { onToggleHabit(habit.habit.id) },
            onClick = { onHabitClick(habit.habit.id) },
            onSetStatus = { status -> onSetStatus(habit.habit.id, status) },
        )
    }
}

@Composable
private fun AllHabitsHeader(uiState: AllHabitsUiState) {
    ScreenHeader(
        title = "All Habits",
        subtitle = "${uiState.totalToday} habits  •  ${uiState.doneToday} done today",
    )
}

/** The one number that drives a return visit: how much of today is left. */
@Composable
private fun TodayProgress(uiState: AllHabitsUiState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (uiState.totalToday > 0 && uiState.doneToday == uiState.totalToday) {
                    "All done for today 🎉"
                } else {
                    "${uiState.doneToday} of ${uiState.totalToday} completed today"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${(uiState.progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { uiState.progress },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun HabitSearchField(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search habits") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = TextFieldDefaults.colors(
            // Material's default container here is a lavender tint — pin it to our own surface.
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CategoryFilterRow(
    categories: List<String>,
    counts: Map<String, Int>,
    selected: String?,
    totalCount: Int,
    onCategorySelect: (String?) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onCategorySelect(null) },
                label = { Text("All  $totalCount") },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
        items(categories) { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onCategorySelect(category) },
                label = { Text("$category  ${counts[category] ?: 0}") },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

@Composable
private fun SectionLabel(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .size(20.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "$count", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun NoMatchesMessage(uiState: AllHabitsUiState) {
    val reason = when {
        uiState.searchQuery.isNotBlank() -> "No habits match \"${uiState.searchQuery}\"."
        uiState.selectedCategory != null -> "No habits in ${uiState.selectedCategory} yet."
        else -> "No habits yet — tap New habit to add your first one."
    }
    Text(
        text = reason,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
    )
}
