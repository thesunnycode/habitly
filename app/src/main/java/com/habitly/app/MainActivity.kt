package com.habitly.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.navigation.compose.rememberNavController
import com.habitly.app.ui.navigation.Destination
import com.habitly.app.ui.navigation.HabitlyNavHost
import com.habitly.app.ui.navigation.HabitlyScaffold
import com.habitly.app.ui.theme.HabitlyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HabitlyTheme {
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
