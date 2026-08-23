package com.habitly.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

val Context.habitlyDataStore by preferencesDataStore(name = "habitly_prefs")
