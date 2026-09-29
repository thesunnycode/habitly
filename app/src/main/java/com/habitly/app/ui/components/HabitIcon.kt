package com.habitly.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NoDrinks
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhonelinkErase
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.SmokeFree
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector
import com.habitly.app.ui.theme.HabitPalette

private val ICONS: Map<String, ImageVector> by lazy {
    linkedMapOf(
        "star" to Icons.Rounded.Star,
        "water_drop" to Icons.Rounded.WaterDrop,
        "menu_book" to Icons.Rounded.MenuBook,
        "fitness_center" to Icons.Rounded.FitnessCenter,
        "self_improvement" to Icons.Rounded.SelfImprovement,
        "run" to Icons.Rounded.DirectionsRun,
        "sleep" to Icons.Rounded.Bedtime,
        "food" to Icons.Rounded.Restaurant,
        "drink" to Icons.Rounded.LocalDrink,
        "walk" to Icons.Rounded.DirectionsWalk,
        "bike" to Icons.Rounded.DirectionsBike,
        "pool" to Icons.Rounded.Pool,
        "spa" to Icons.Rounded.Spa,
        "favorite" to Icons.Rounded.Favorite,
        "psychology" to Icons.Rounded.Psychology,
        "school" to Icons.Rounded.School,
        "code" to Icons.Rounded.Code,
        "brush" to Icons.Rounded.Brush,
        "music" to Icons.Rounded.MusicNote,
        "language" to Icons.Rounded.Translate,
        "edit" to Icons.Rounded.EditNote,
        "savings" to Icons.Rounded.Savings,
        "work" to Icons.Rounded.Work,
        "cleaning" to Icons.Rounded.CleaningServices,
        "eco" to Icons.Rounded.Eco,
        "pets" to Icons.Rounded.Pets,
        "phone_off" to Icons.Rounded.PhonelinkErase,
        "sunny" to Icons.Rounded.WbSunny,
        "alarm" to Icons.Rounded.Alarm,
        "medication" to Icons.Rounded.Medication,
        "smoke_free" to Icons.Rounded.SmokeFree,
        "no_drinks" to Icons.Rounded.NoDrinks,
        "coffee" to Icons.Rounded.Coffee,
        "family" to Icons.Rounded.FamilyRestroom,
        "call" to Icons.Rounded.Call,
        "camera" to Icons.Rounded.PhotoCamera,
    )
}

/** Maps a habit's stored icon key (chosen in Add/Edit) to a concrete Material icon. */
fun habitIconFor(key: String): ImageVector = ICONS[key] ?: Icons.Rounded.Star

val HABIT_ICON_KEYS: List<String> = listOf(
    "star", "water_drop", "menu_book", "fitness_center", "self_improvement", "run", "sleep", "food",
    "drink", "walk", "bike", "pool", "spa", "favorite", "psychology", "school", "code", "brush",
    "music", "language", "edit", "savings", "work", "cleaning", "eco", "pets", "phone_off", "sunny",
    "alarm", "medication", "smoke_free", "no_drinks", "coffee", "family", "call", "camera",
)

val HABIT_COLOR_SWATCHES = HabitPalette.seeds
