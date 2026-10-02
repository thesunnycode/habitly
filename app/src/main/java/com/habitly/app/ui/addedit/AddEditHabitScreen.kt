package com.habitly.app.ui.addedit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.habitly.app.domain.DEFAULT_CATEGORIES
import com.habitly.app.domain.FrequencyType
import com.habitly.app.domain.HabitSchedule
import com.habitly.app.domain.label
import com.habitly.app.ui.components.CheckState
import com.habitly.app.ui.components.ConfirmSheet
import com.habitly.app.ui.components.HABIT_COLOR_SWATCHES
import com.habitly.app.ui.components.HabitGridCard
import com.habitly.app.ui.components.IconTile
import com.habitly.app.ui.components.PrimaryButton
import com.habitly.app.ui.components.ReminderTimeSheet
import com.habitly.app.ui.components.ScreenHeader
import com.habitly.app.ui.components.SectionLabel
import com.habitly.app.ui.components.SegmentedPill
import com.habitly.app.ui.components.SettingsGroup
import com.habitly.app.ui.components.SettingsRow
import com.habitly.app.ui.theme.LocalHabitlyColors
import com.habitly.app.ui.theme.habitTones
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.random.Random

/** Names for [HABIT_COLOR_SWATCHES], in the same order, so a swatch is never identified by colour alone. */
private val COLOR_NAMES = listOf(
    "Orange", "Blue", "Violet", "Green", "Red", "Yellow", "Cyan", "Pink", "Lime", "Indigo", "Salmon", "Grey",
)

private const val MIN_INTERVAL = 2
private const val MAX_INTERVAL = 30

private fun repeatLabel(type: FrequencyType) = when (type) {
    FrequencyType.DAILY -> "Daily"
    FrequencyType.SPECIFIC_DAYS -> "Days"
    FrequencyType.INTERVAL -> "Interval"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditHabitScreen(
    uiState: AddEditUiState,
    onNameChange: (String) -> Unit,
    onIconChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onFrequencyTypeChange: (FrequencyType) -> Unit,
    onSpecificDaysChange: (Set<DayOfWeek>) -> Unit,
    onIntervalDaysChange: (Int?) -> Unit,
    onTargetCountChange: (Int?) -> Unit,
    onUnitChange: (String) -> Unit,
    onReminderTimeChange: (String?) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    weekStart: DayOfWeek = DayOfWeek.MONDAY,
) {
    val isNew = uiState.habitId == null
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    var showIcons by rememberSaveable { mutableStateOf(false) }
    var showGoal by rememberSaveable { mutableStateOf(false) }
    var showReminder by rememberSaveable { mutableStateOf(false) }
    val requestClose = { if (uiState.isDirty) showDiscard = true else onClose() }

    BackHandler(enabled = uiState.isDirty) { showDiscard = true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Insets are handled per region below: the header clears the status bar, the bottom bar clears
        // the navigation bar and the keyboard.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ScreenHeader(
                title = if (isNew) "New habit" else "Edit habit",
                onClose = requestClose,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp),
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                PrimaryButton(
                    text = if (isNew) "Create habit" else "Save",
                    onClick = onSave,
                    enabled = uiState.canSave,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            FormPreview(uiState, weekStart)
            NameField(uiState, onNameChange, onIconClick = { showIcons = true })
            ColorSection(selected = uiState.colorHex, onSelect = onColorChange)
            CategorySection(uiState, onCategoryChange)
            RepeatSection(uiState, weekStart, onFrequencyTypeChange, onSpecificDaysChange, onIntervalDaysChange)
            SettingsGroup {
                SettingsRow(
                    title = "Daily goal",
                    icon = Icons.Rounded.Flag,
                    value = uiState.targetCount?.let { "$it ${uiState.unit.ifBlank { "times" }}" } ?: "Off",
                    onClick = { showGoal = true },
                )
                SettingsRow(
                    title = "Reminder",
                    icon = Icons.Rounded.Alarm,
                    value = uiState.reminderTime ?: "Off",
                    onClick = { showReminder = true },
                    showDivider = true,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showIcons) {
        IconPickerSheet(
            selectedIcon = uiState.icon,
            colorHex = uiState.colorHex,
            onSelect = { onIconChange(it); showIcons = false },
            onDismiss = { showIcons = false },
        )
    }
    if (showGoal) {
        GoalSheet(
            targetCount = uiState.targetCount,
            unit = uiState.unit,
            onSet = { target, unit ->
                // A goal of one is just done / not done, which is what "no goal" already means.
                if (target > 1) {
                    onTargetCountChange(target)
                    onUnitChange(unit)
                } else {
                    onTargetCountChange(null)
                    onUnitChange("")
                }
                showGoal = false
            },
            onRemove = { onTargetCountChange(null); onUnitChange(""); showGoal = false },
            onDismiss = { showGoal = false },
        )
    }
    if (showReminder) {
        ReminderTimeSheet(
            time = uiState.reminderTime,
            onSet = { onReminderTimeChange(it); showReminder = false },
            onTurnOff = { onReminderTimeChange(null); showReminder = false },
            onDismiss = { showReminder = false },
        )
    }
    if (showDiscard) {
        ConfirmSheet(
            title = "Discard changes?",
            message = "Your changes to this habit won't be saved.",
            confirmLabel = "Discard",
            onConfirm = { showDiscard = false; onClose() },
            onDismiss = { showDiscard = false },
            icon = Icons.Rounded.Close,
        )
    }
}

/** The card as it will look on Today, with a fixed sample history so the colour choice is easy to judge. */
@Composable
private fun FormPreview(uiState: AddEditUiState, weekStart: DayOfWeek) {
    val end = remember { LocalDate.now() }
    val sampleLevels = remember(end) {
        val random = Random(42)
        (0 until 16 * 7).mapNotNull { offset ->
            if (random.nextFloat() < 0.6f) end.minusDays(offset.toLong()) to 4 else null
        }.toMap()
    }
    val schedule = HabitSchedule(uiState.frequencyType, uiState.specificDays, uiState.intervalDays)
    val meta = listOfNotNull(
        schedule.label(),
        uiState.targetCount?.let { "$it ${uiState.unit.ifBlank { "times" }}" },
    ).joinToString(" · ")
    Box(
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = "Preview of ${uiState.name.ifBlank { "your new habit" }}"
        },
    ) {
        HabitGridCard(
            name = uiState.name.ifBlank { "Your new habit" },
            iconKey = uiState.icon,
            colorHex = uiState.colorHex,
            meta = meta,
            checkState = CheckState.EMPTY,
            levels = sampleLevels,
            onCheck = {},
            onClick = {},
            onLongClick = {},
            endDate = end,
            weekStart = weekStart,
            countsUp = uiState.targetCount != null,
        )
        // Swallows every touch so the preview can't be ticked or pressed.
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                },
        )
    }
}

@Composable
private fun NameField(uiState: AddEditUiState, onNameChange: (String) -> Unit, onIconClick: () -> Unit) {
    Column {
        TextField(
            value = uiState.name,
            onValueChange = onNameChange,
            placeholder = { Text("e.g. Drink water") },
            leadingIcon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClick = onIconClick)
                        .semantics { contentDescription = "Change icon" },
                    contentAlignment = Alignment.Center,
                ) {
                    IconTile(iconKey = uiState.icon, colorHex = uiState.colorHex, size = 32.dp)
                }
            },
            isError = uiState.nameError != null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            shape = RoundedCornerShape(16.dp),
            colors = formFieldColors(),
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Habit name" },
        )
        uiState.nameError?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSection(selected: String, onSelect: (String) -> Unit) {
    Column {
        SectionLabel("Colour")
        Spacer(Modifier.height(10.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            HABIT_COLOR_SWATCHES.forEachIndexed { index, seed ->
                val isSelected = selected.equals(seed, ignoreCase = true)
                val name = COLOR_NAMES.getOrElse(index) { seed }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(seed) })
                        .semantics { contentDescription = name },
                    contentAlignment = Alignment.Center,
                ) {
                    // 36dp swatch, then a 3dp gap, then a 2dp ring: 46dp in all.
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .border(
                                width = 2.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                shape = CircleShape,
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(habitTones(seed).full),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySection(uiState: AddEditUiState, onCategoryChange: (String) -> Unit) {
    var addingNew by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }
    val categories = (uiState.existingCategories + DEFAULT_CATEGORIES + uiState.category)
        .filter { it.isNotBlank() }
        .distinct()
    val commitNew = {
        if (newName.isNotBlank()) onCategoryChange(newName.trim())
        newName = ""
        addingNew = false
    }

    Column {
        SectionLabel("Category")
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            categories.forEach { category ->
                FilterChip(
                    selected = uiState.category == category,
                    onClick = { onCategoryChange(category) },
                    label = { Text(category) },
                    leadingIcon = if (uiState.category == category) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                )
            }
            if (!addingNew) {
                FilterChip(
                    selected = false,
                    onClick = { addingNew = true },
                    label = { Text("New…") },
                    leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        if (addingNew) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = newName,
                    onValueChange = { newName = it.take(24) },
                    placeholder = { Text("New category") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { commitNew() }),
                    shape = RoundedCornerShape(16.dp),
                    colors = formFieldColors(),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = commitNew,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = LocalHabitlyColors.current.primaryButton,
                        contentColor = LocalHabitlyColors.current.onPrimaryButton,
                    ),
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = "Add category")
                }
            }
        }
    }
}

@Composable
private fun RepeatSection(
    uiState: AddEditUiState,
    weekStart: DayOfWeek,
    onFrequencyTypeChange: (FrequencyType) -> Unit,
    onSpecificDaysChange: (Set<DayOfWeek>) -> Unit,
    onIntervalDaysChange: (Int?) -> Unit,
) {
    Column {
        SectionLabel("Repeat")
        Spacer(Modifier.height(10.dp))
        SegmentedPill(
            options = FrequencyType.entries.toList(),
            selected = uiState.frequencyType,
            onSelect = onFrequencyTypeChange,
            label = ::repeatLabel,
        )
        when (uiState.frequencyType) {
            FrequencyType.DAILY -> Unit
            FrequencyType.SPECIFIC_DAYS -> {
                Spacer(Modifier.height(14.dp))
                DayToggles(uiState.specificDays, uiState.colorHex, weekStart, onSpecificDaysChange)
                if (uiState.specificDays.isEmpty()) {
                    Text(
                        text = "Pick at least one day",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            FrequencyType.INTERVAL -> {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Every", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(12.dp))
                    Stepper(
                        value = uiState.intervalDays ?: MIN_INTERVAL,
                        onValueChange = onIntervalDaysChange,
                        range = MIN_INTERVAL..MAX_INTERVAL,
                        valueDescription = { "every $it days" },
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("days", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun DayToggles(
    selected: Set<DayOfWeek>,
    colorHex: String,
    weekStart: DayOfWeek,
    onChange: (Set<DayOfWeek>) -> Unit,
) {
    val tones = habitTones(colorHex)
    val scheme = MaterialTheme.colorScheme
    val locale = Locale.getDefault()
    val days = (0L until 7L).map { weekStart.plus(it) }
    Row(modifier = Modifier.fillMaxWidth()) {
        days.forEach { day ->
            val on = day in selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .toggleable(
                        value = on,
                        role = Role.Checkbox,
                        onValueChange = { onChange(if (it) selected + day else selected - day) },
                    )
                    .semantics {
                        contentDescription = day.getDisplayName(TextStyle.FULL, locale)
                        stateDescription = if (on) "Repeats" else "Off"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (on) tones.full else scheme.surface)
                        .border(1.dp, if (on) Color.Transparent else scheme.outline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = day.getDisplayName(TextStyle.NARROW, locale),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) tones.onFull else scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** "[−] n [+]" with 48dp buttons, clamped to [range]. */
@Composable
internal fun Stepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    valueDescription: (Int) -> String,
) {
    val buttonColors = IconButtonDefaults.iconButtonColors(
        containerColor = LocalHabitlyColors.current.surfaceRaised,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { onValueChange((value - 1).coerceIn(range)) },
            enabled = value > range.first,
            modifier = Modifier.size(48.dp),
            colors = buttonColors,
        ) {
            Icon(Icons.Rounded.Remove, contentDescription = "Decrease")
        }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(64.dp)
                .semantics { contentDescription = valueDescription(value) },
        )
        IconButton(
            onClick = { onValueChange((value + 1).coerceIn(range)) },
            enabled = value < range.last,
            modifier = Modifier.size(48.dp),
            colors = buttonColors,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Increase")
        }
    }
}

/** Filled `surface` field with no indicator line, shared by every text field on the form. */
@Composable
internal fun formFieldColors(): TextFieldColors {
    val surface = MaterialTheme.colorScheme.surface
    return TextFieldDefaults.colors(
        focusedContainerColor = surface,
        unfocusedContainerColor = surface,
        errorContainerColor = surface,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
    )
}
