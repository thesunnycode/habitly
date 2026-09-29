package com.habitly.app.ui.navigation

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.SavedStateHandle
import com.habitly.app.data.repository.HabitDataSource
import com.habitly.app.ui.components.LocalSnackbarHostState
import kotlinx.coroutines.flow.filterNotNull

/** Set on the previous back-stack entry by Habit detail when it archives a habit; the value is the habit id. */
const val ARCHIVED_HABIT_KEY = "archived_habit"

/**
 * Watches this entry's [savedStateHandle] for a habit that was just archived elsewhere and offers
 * to take it back: "Archived ‘Read’" with Undo, which un-archives it.
 */
@Composable
fun ArchivedUndoEffect(
    savedStateHandle: SavedStateHandle,
    repository: HabitDataSource,
    snackbarHostState: SnackbarHostState = LocalSnackbarHostState.current,
) {
    LaunchedEffect(savedStateHandle) {
        // Collecting (rather than keying an effect on the value) keeps this coroutine alive when the
        // key is removed; the key stays until the snackbar is done, so a configuration change
        // mid-message shows it again instead of losing the Undo.
        savedStateHandle.getStateFlow<Long?>(ARCHIVED_HABIT_KEY, null).filterNotNull().collect { id ->
            val name = repository.getHabit(id)?.name ?: "habit"
            val result = snackbarHostState.showSnackbar(
                message = "Archived ‘$name’",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            savedStateHandle.remove<Long>(ARCHIVED_HABIT_KEY)
            if (result == SnackbarResult.ActionPerformed) repository.setArchived(id, false)
        }
    }
}
