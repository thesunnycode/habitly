package com.habitly.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector

/** Maps a habit's stored icon key (chosen in Add/Edit) to a concrete Material icon. */
fun habitIconFor(key: String): ImageVector = when (key) {
    "water_drop" -> Icons.Filled.WaterDrop
    "menu_book" -> Icons.Filled.MenuBook
    "fitness_center" -> Icons.Filled.FitnessCenter
    "self_improvement" -> Icons.Filled.SelfImprovement
    "run" -> Icons.Filled.DirectionsRun
    "sleep" -> Icons.Filled.NightsStay
    "food" -> Icons.Filled.Restaurant
    "drink" -> Icons.Filled.LocalDrink
    else -> Icons.Filled.Star
}

val HABIT_ICON_KEYS = listOf(
    "star", "water_drop", "menu_book", "fitness_center", "self_improvement",
    "run", "sleep", "food", "drink",
)

val HABIT_COLOR_SWATCHES = listOf(
    "#6B9080", "#F76C5E", "#F6BD60", "#4C6EF5", "#AE3EC9",
    "#12B886", "#E64980", "#1098AD", "#F08C00", "#495057",
)
