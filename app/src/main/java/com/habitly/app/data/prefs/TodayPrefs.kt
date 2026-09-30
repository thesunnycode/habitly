package com.habitly.app.data.prefs

import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek

/** The slice of settings the Today screen reads, so its ViewModel can be tested without DataStore. */
interface TodayPrefs {
    val userName: Flow<String?>
    val weekStart: Flow<DayOfWeek>
    val lastCelebratedDate: Flow<String?>
    suspend fun setLastCelebratedDate(date: String)
}
