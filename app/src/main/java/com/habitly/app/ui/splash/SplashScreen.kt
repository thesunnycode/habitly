package com.habitly.app.ui.splash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.habitly.app.data.prefs.OnboardingPrefs
import com.habitly.app.ui.navigation.Destination
import kotlinx.coroutines.flow.first

@Composable
fun SplashScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val prefs = OnboardingPrefs(context)
        val completed = prefs.hasCompletedOnboarding.first()
        onNavigate(if (completed) Destination.Home.route else Destination.Onboarding.route)
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Habitly", style = MaterialTheme.typography.headlineMedium)
    }
}
