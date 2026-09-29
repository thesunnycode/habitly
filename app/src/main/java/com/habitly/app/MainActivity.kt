package com.habitly.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.habitly.app.data.prefs.OnboardingPrefs
import com.habitly.app.data.prefs.SettingsPrefs
import com.habitly.app.data.prefs.ThemeMode
import com.habitly.app.ui.navigation.Destination
import com.habitly.app.ui.navigation.HabitlyNavHost
import com.habitly.app.ui.navigation.HabitlyScaffold
import com.habitly.app.ui.theme.HabitlyTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var startRoute by mutableStateOf<String?>(null)

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { startRoute == null }
        lifecycleScope.launch {
            val done = OnboardingPrefs(this@MainActivity).hasCompletedOnboarding.first()
            startRoute = if (done) Destination.Today.route else Destination.Onboarding.route
        }
        enableEdgeToEdge()
        setContent {
            val route = startRoute ?: return@setContent
            val windowSizeClass = calculateWindowSizeClass(this)
            val systemDark = isSystemInDarkTheme()
            val themeMode by remember { SettingsPrefs(this).themeMode }.collectAsState(initial = ThemeMode.DARK)
            val darkTheme = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> systemDark
            }
            DisposableEffect(darkTheme) {
                val transparent = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) SystemBarStyle.dark(transparent)
                    else SystemBarStyle.light(transparent, transparent),
                    navigationBarStyle = if (darkTheme) SystemBarStyle.dark(transparent)
                    else SystemBarStyle.light(transparent, transparent),
                )
                onDispose {}
            }
            HabitlyTheme(darkTheme = darkTheme) {
                Surface {
                    val navController = rememberNavController()
                    HabitlyScaffold(navController = navController) { padding ->
                        androidx.compose.foundation.layout.Box(
                            modifier = androidx.compose.ui.Modifier.padding(padding),
                        ) {
                            HabitlyNavHost(
                                navController = navController,
                                startDestination = route,
                                windowWidthSizeClass = windowSizeClass.widthSizeClass,
                            )
                        }
                    }
                }
            }
        }
    }
}
