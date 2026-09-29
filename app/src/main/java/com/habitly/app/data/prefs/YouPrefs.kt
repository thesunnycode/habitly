package com.habitly.app.data.prefs

import kotlinx.coroutines.flow.Flow

/** The slice of settings the You tab reads and writes, so its ViewModel can be tested without DataStore. */
interface YouPrefs {
    val userName: Flow<String?>
    suspend fun setUserName(name: String?)
}
