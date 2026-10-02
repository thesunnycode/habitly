package com.habitly.app.ui.habits

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.repository.HabitWithStats
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.HabitSchedule
import com.habitly.app.domain.HabitTemplate
import com.habitly.app.domain.label
import com.habitly.app.ui.components.CheckState
import com.habitly.app.ui.components.ConfirmSheet
import com.habitly.app.ui.components.EmptyState
import com.habitly.app.ui.components.HabitRow
import com.habitly.app.ui.components.LocalSnackbarHostState
import com.habitly.app.ui.components.ScreenHeader
import com.habitly.app.ui.components.SectionLabel
import com.habitly.app.ui.components.SegmentedPill
import com.habitly.app.ui.theme.LocalHabitlyColors
import com.habitly.app.ui.today.TemplateSheet
import kotlinx.coroutines.launch
import java.time.LocalDate

private val CARD_SHAPE = RoundedCornerShape(20.dp)

/** Left edge of a row's text inside a card: card padding + row padding + icon + gap. */
private val DIVIDER_INSET = 60.dp

@Composable
fun HabitsScreen(
    uiState: HabitsUiState,
    onSegmentChange: (HabitsSegment) -> Unit,
    onQueryChange: (String) -> Unit,
    onCategorySelect: (String?) -> Unit,
    onSortChange: (HabitSort) -> Unit,
    onClearFilters: () -> Unit,
    onArchive: (Long) -> Unit,
    onRestore: (Long) -> Unit,
    onDeleteForever: (Long) -> Unit,
    onMove: (fromId: Long, toId: Long) -> Unit,
    onHabitClick: (Long) -> Unit,
    onAddHabit: (HabitTemplate?) -> Unit,
    onCreateWithAi: () -> Unit = {},
) {
    var showTemplates by rememberSaveable { mutableStateOf(false) }
    var deleteTargetId by rememberSaveable { mutableStateOf<Long?>(null) }
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()

    val archiveWithUndo: (Long, String) -> Unit = { id, name ->
        onArchive(id)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Archived ‘$name’",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onRestore(id)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        // The bottom padding keeps the last row clear of the floating nav bar.
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            ScreenHeader(
                title = "Habits",
                subtitle = "${uiState.activeCount} active",
                actions = {
                    if (uiState.segment == HabitsSegment.ACTIVE) {
                        SortMenu(uiState.sort, onSortChange)
                    }
                    IconButton(onClick = onCreateWithAi) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = "Create with AI")
                    }
                    IconButton(onClick = { showTemplates = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = "New habit")
                    }
                },
            )
        }
        item(key = "segments") {
            SegmentedPill(
                options = HabitsSegment.entries,
                selected = uiState.segment,
                onSelect = onSegmentChange,
                label = {
                    when (it) {
                        HabitsSegment.ACTIVE -> "Active ${uiState.activeCount}"
                        HabitsSegment.ARCHIVED -> "Archived ${uiState.archivedCount}"
                    }
                },
            )
        }
        if (!uiState.isLoading) {
            when (uiState.segment) {
                HabitsSegment.ACTIVE -> activeContent(
                    uiState = uiState,
                    onQueryChange = onQueryChange,
                    onCategorySelect = onCategorySelect,
                    onClearFilters = onClearFilters,
                    onArchive = archiveWithUndo,
                    onMove = onMove,
                    onHabitClick = onHabitClick,
                    onNewHabit = { showTemplates = true },
                )
                HabitsSegment.ARCHIVED -> archivedContent(
                    archived = uiState.archived,
                    onRestore = onRestore,
                    onDelete = { deleteTargetId = it },
                    onHabitClick = onHabitClick,
                )
            }
        }
    }

    if (showTemplates) {
        TemplateSheet(
            onDismiss = { showTemplates = false },
            onPick = { template ->
                showTemplates = false
                onAddHabit(template)
            },
            onCreateWithAi = {
                showTemplates = false
                onCreateWithAi()
            },
        )
    }

    val deleteTarget = deleteTargetId?.let { id -> uiState.archived.firstOrNull { it.id == id } }
    if (deleteTarget != null) {
        ConfirmSheet(
            title = "Delete ‘${deleteTarget.name}’ forever?",
            message = "This removes the habit and all of its history. This can't be undone.",
            confirmLabel = "Delete",
            icon = Icons.Rounded.DeleteOutline,
            onConfirm = {
                deleteTargetId = null
                onDeleteForever(deleteTarget.id)
            },
            onDismiss = { deleteTargetId = null },
        )
    }
}

private fun LazyListScope.activeContent(
    uiState: HabitsUiState,
    onQueryChange: (String) -> Unit,
    onCategorySelect: (String?) -> Unit,
    onClearFilters: () -> Unit,
    onArchive: (Long, String) -> Unit,
    onMove: (Long, Long) -> Unit,
    onHabitClick: (Long) -> Unit,
    onNewHabit: () -> Unit,
) {
    if (uiState.activeCount == 0) {
        item(key = "empty-active") {
            EmptyState(
                icon = Icons.Rounded.GridView,
                title = "No habits yet",
                message = "Add your first habit and watch the grid fill in.",
                actionLabel = "New habit",
                onAction = onNewHabit,
            )
        }
        return
    }
    item(key = "search") { SearchField(uiState.query, onQueryChange) }
    item(key = "chips") {
        CategoryChips(uiState.categories, uiState.activeCount, uiState.selectedCategory, onCategorySelect)
    }
    if (uiState.groups.isEmpty()) {
        item(key = "no-matches") {
            EmptyState(
                icon = Icons.Rounded.SearchOff,
                title = "No habits match",
                message = "Try a different word or clear the filters.",
                actionLabel = "Clear filters",
                onAction = onClearFilters,
            )
        }
        return
    }
    // Reordering only makes sense against the custom order, over the whole unfiltered list.
    val canReorder = uiState.sort == HabitSort.CUSTOM && !uiState.isFiltering
    uiState.groups.forEach { group ->
        item(key = "label-${group.category}") {
            SectionLabel(group.category, count = group.habits.size, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
        }
        item(key = "group-${group.category}") {
            ActiveGroupCard(group, canReorder, onArchive, onMove, onHabitClick)
        }
    }
}

private fun LazyListScope.archivedContent(
    archived: List<HabitEntity>,
    onRestore: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onHabitClick: (Long) -> Unit,
) {
    if (archived.isEmpty()) {
        item(key = "empty-archived") {
            EmptyState(
                icon = Icons.Rounded.Archive,
                title = "Nothing archived",
                message = "Habits you archive rest here, with their history kept.",
            )
        }
        return
    }
    item(key = "archived-card") {
        HabitsCard {
            archived.forEachIndexed { index, habit ->
                key(habit.id, HabitsSegment.ARCHIVED) {
                    if (index > 0) RowDivider()
                    SwipeRow(icon = Icons.Rounded.Unarchive, onSwiped = { onRestore(habit.id) }) {
                        HabitRow(
                            name = habit.name,
                            iconKey = habit.icon,
                            colorHex = habit.colorHex,
                            supporting = archivedSupporting(habit),
                            last7 = emptyList(),
                            onClick = { onHabitClick(habit.id) },
                            muted = true,
                            modifier = Modifier.padding(start = 12.dp),
                            trailing = {
                                TextButton(onClick = { onRestore(habit.id) }) { Text("Restore") }
                                ArchivedOverflow(habit.name, onDelete = { onDelete(habit.id) })
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Local drag state for one group: the order shown while dragging and until the saved order arrives. */
private class ReorderState {
    var override by mutableStateOf<List<Long>?>(null)
    var draggedId by mutableStateOf<Long?>(null)
    var offset by mutableFloatStateOf(0f)
    var rowHeight by mutableIntStateOf(0)
    var startOrder: List<Long> = emptyList()
    /** The order last drawn, read when a drag begins. */
    var visible: List<Long> = emptyList()
}

@Composable
private fun ActiveGroupCard(
    group: HabitGroup,
    canReorder: Boolean,
    onArchive: (Long, String) -> Unit,
    onMove: (Long, Long) -> Unit,
    onHabitClick: (Long) -> Unit,
) {
    val ids = group.habits.map { it.habit.id }
    val byId = group.habits.associateBy { it.habit.id }
    val reorder = remember { ReorderState() }
    val currentOnMove by rememberUpdatedState(onMove)
    // Once the persisted order arrives (or the list changes for any other reason), drop the local copy.
    LaunchedEffect(ids) { if (reorder.draggedId == null) reorder.override = null }
    val shown = reorder.override?.takeIf { it.toSet() == ids.toSet() } ?: ids
    SideEffect { reorder.visible = shown }

    HabitsCard {
        shown.forEachIndexed { index, id ->
            val habit = byId[id] ?: return@forEachIndexed
            key(id, HabitsSegment.ACTIVE) {
                val dragging = reorder.draggedId == id
                Column(
                    modifier = Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragging) reorder.offset else 0f
                            shadowElevation = if (dragging) 8.dp.toPx() else 0f
                        }
                        .background(if (dragging) LocalHabitlyColors.current.surfaceRaised else Color.Transparent)
                        .onSizeChanged { reorder.rowHeight = it.height }
                        .semantics {
                            if (canReorder) {
                                customActions = listOfNotNull(
                                    shown.getOrNull(index - 1)?.let { prev ->
                                        CustomAccessibilityAction("Move up") { currentOnMove(id, prev); true }
                                    },
                                    shown.getOrNull(index + 1)?.let { next ->
                                        CustomAccessibilityAction("Move down") { currentOnMove(id, next); true }
                                    },
                                )
                            }
                        },
                ) {
                    if (index > 0 && !dragging) RowDivider()
                    SwipeRow(icon = Icons.Rounded.Archive, onSwiped = { onArchive(id, habit.habit.name) }) {
                        HabitRow(
                            name = habit.habit.name,
                            iconKey = habit.habit.icon,
                            colorHex = habit.habit.colorHex,
                            supporting = activeSupporting(habit),
                            last7 = habit.last7DaysStatus.map { it.toCheckState() },
                            onClick = { onHabitClick(id) },
                            modifier = Modifier.padding(start = 12.dp, end = if (canReorder) 0.dp else 12.dp),
                            trailing = if (canReorder) {
                                { DragHandle(habit.habit.name, id, reorder, onDrop = { from, to -> currentOnMove(from, to) }) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DragHandle(name: String, id: Long, reorder: ReorderState, onDrop: (Long, Long) -> Unit) {
    val currentOnDrop by rememberUpdatedState(onDrop)
    Box(
        modifier = Modifier
            .size(48.dp)
            .pointerInput(id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        // Seed the local order from whatever is on screen right now.
                        reorder.startOrder = reorder.visible
                        reorder.override = reorder.visible
                        reorder.draggedId = id
                        reorder.offset = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        val list = reorder.override?.toMutableList() ?: return@detectDragGesturesAfterLongPress
                        reorder.offset += amount.y
                        val i = list.indexOf(id)
                        val half = reorder.rowHeight / 2f
                        if (reorder.offset > half && i < list.lastIndex) {
                            list[i] = list[i + 1]; list[i + 1] = id
                            reorder.offset -= reorder.rowHeight
                            reorder.override = list
                        } else if (reorder.offset < -half && i > 0) {
                            list[i] = list[i - 1]; list[i - 1] = id
                            reorder.offset += reorder.rowHeight
                            reorder.override = list
                        }
                    },
                    onDragEnd = {
                        val finalIndex = reorder.override?.indexOf(id) ?: -1
                        val target = reorder.startOrder.getOrNull(finalIndex)
                        reorder.draggedId = null
                        reorder.offset = 0f
                        if (target != null && target != id) currentOnDrop(id, target) else reorder.override = null
                    },
                    onDragCancel = {
                        reorder.draggedId = null
                        reorder.offset = 0f
                        reorder.override = null
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.DragIndicator,
            contentDescription = "Reorder $name",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Swipe end-to-start to trigger [onSwiped]; the revealed background shows [icon]. */
@Composable
private fun SwipeRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onSwiped: () -> Unit,
    content: @Composable () -> Unit,
) {
    val currentOnSwiped by rememberUpdatedState(onSwiped)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) currentOnSwiped()
            // The row leaves the list once the data updates; keyed by id and segment, it never
            // comes back still dismissed.
            value == SwipeToDismissBoxValue.EndToStart
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LocalHabitlyColors.current.surfaceRaised)
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            }
        },
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.surface)) { content() }
    }
}

@Composable
private fun SortMenu(current: HabitSort, onSortChange: (HabitSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort habits")
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = LocalHabitlyColors.current.surfaceRaised,
        ) {
            HabitSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(sort.label) },
                    trailingIcon = {
                        if (sort == current) Icon(Icons.Rounded.Check, contentDescription = "Selected")
                    },
                    onClick = { open = false; onSortChange(sort) },
                )
            }
        }
    }
}

private val HabitSort.label: String
    get() = when (this) {
        HabitSort.CUSTOM -> "Custom order"
        HabitSort.NAME -> "Name"
        HabitSort.STREAK -> "Longest streak"
        HabitSort.CREATED -> "Newest first"
    }

@Composable
private fun ArchivedOverflow(name: String, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More options for $name")
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = LocalHabitlyColors.current.surfaceRaised,
        ) {
            DropdownMenuItem(
                text = { Text("Delete permanently", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                },
                onClick = { open = false; onDelete() },
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search habits") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = LocalHabitlyColors.current.surfaceRaised,
            unfocusedContainerColor = LocalHabitlyColors.current.surfaceRaised,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CategoryChips(
    categories: List<Pair<String, Int>>,
    total: Int,
    selected: String?,
    onCategorySelect: (String?) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "all") { CategoryChip("All $total", selected == null) { onCategorySelect(null) } }
        items(categories, key = { it.first }) { (name, count) ->
            CategoryChip("$name $count", selected == name) { onCategorySelect(name) }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else {
            null
        },
    )
}

@Composable
private fun HabitsCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CARD_SHAPE,
        color = MaterialTheme.colorScheme.surface,
        border = if (LocalHabitlyColors.current.isDark) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) { content() }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = DIVIDER_INSET),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline,
    )
}

/** "Daily · 12-day streak", or just the schedule before a streak starts. */
internal fun activeSupporting(habit: HabitWithStats): String {
    val schedule = habit.schedule.label()
    return if (habit.currentStreak > 0) "$schedule · ${habit.currentStreak}-day streak" else schedule
}

private fun archivedSupporting(habit: HabitEntity): String {
    val schedule = HabitSchedule.parse(
        habit.frequencyType, habit.frequencyDays, habit.intervalDays,
        // The label never reads the start date.
        startDate = LocalDate.ofEpochDay(0),
    )
    return "${habit.category} · ${schedule.label()}"
}

private fun CompletionStatus?.toCheckState(): CheckState = when (this) {
    CompletionStatus.DONE -> CheckState.DONE
    CompletionStatus.SKIPPED -> CheckState.SKIPPED
    CompletionStatus.PARTIAL -> CheckState.IN_PROGRESS
    CompletionStatus.MISSED, null -> CheckState.EMPTY
}
