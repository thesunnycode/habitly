package com.habitly.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.compose.rememberNavController
import com.habitly.app.data.prefs.SettingsPrefs
import com.habitly.app.data.prefs.ThemeMode
import com.habitly.app.ui.navigation.Destination
import com.habitly.app.ui.navigation.HabitlyNavHost
import com.habitly.app.ui.navigation.HabitlyScaffold
import com.habitly.app.ui.theme.HabitlyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val systemDark = isSystemInDarkTheme()
            val themeMode by remember { SettingsPrefs(this).themeMode }.collectAsState(initial = ThemeMode.SYSTEM)
            val darkTheme = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> systemDark
            }
            HabitlyTheme(darkTheme = darkTheme) {
                Surface {
                    val navController = rememberNavController()
                    HabitlyScaffold(navController = navController) { padding ->
                        androidx.compose.foundation.layout.Box(
                            modifier = androidx.compose.ui.Modifier.padding(padding),
                        ) {
                            HabitlyNavHost(navController = navController, startDestination = Destination.Splash.route)
                        }
                    }
                }
            }
        }
    }
}
