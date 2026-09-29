package com.habitly.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.habitly.app.data.local.AppDatabase
import com.habitly.app.data.repository.HabitRepository

@Composable
fun rememberHabitRepository(): HabitRepository {
    val context = LocalContext.current.applicationContext
    return remember {
        val db = AppDatabase.getInstance(context)
        HabitRepository(db.habitDao(), db.habitLogDao(), db.achievementDao())
    }
}

@Composable
inline fun <reified VM : ViewModel> habitlyViewModel(key: String? = null, crossinline create: () -> VM): VM =
    viewModel(key = key, factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return create() as T
        }
    })
