package com.habitly.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.habitly.app.data.prefs.OnboardingPrefs
import kotlinx.coroutines.launch

private data class OnboardingPage(val title: String, val body: String)

private val PAGES = listOf(
    OnboardingPage("Build habits that stick", "Track daily habits with one tap and watch your streaks grow."),
    OnboardingPage("See your progress", "Calendar heatmaps and charts show exactly how consistent you've been."),
    OnboardingPage("Stay on track", "Get a gentle reminder for each habit at the time that works for you."),
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { PAGES.size })

    // The notification permission is asked for after the first check-in, not here — see
    // OnboardingPrefs.hasShownNotificationPrompt.
    fun finish() {
        scope.launch {
            OnboardingPrefs(context).setCompleted()
            onFinished()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = PAGES[page].title, style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = PAGES[page].body,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            if (pagerState.currentPage == PAGES.lastIndex) {
                Button(onClick = { finish() }) { Text("Get Started") }
            } else {
                Button(onClick = {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }) { Text("Next") }
                TextButton(onClick = { finish() }) { Text("Skip") }
            }
        }
    }
}
