package com.habitly.app.ui.today

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.habitly.app.ml.Forecast
import com.habitly.app.ml.ModelMetrics
import com.habitly.app.ml.RiskModel
import com.habitly.app.ui.components.HabitlySheet
import com.habitly.app.ui.components.HabitlySheetBody
import com.habitly.app.ui.theme.HabitlyTheme
import com.habitly.app.ui.theme.LocalHabitlyColors
import java.util.Locale
import kotlin.math.roundToInt

private val LOOKS_AT = listOf(
    "The day of the week",
    "How you've done lately (last week, the last few months, and the same weekday)",
    "Your streak and whether you missed or rested last time",
    "How long since you last did it",
    "How your other habits went the last few days",
    "How new the habit is",
    "Whether it has a reminder",
)

/** "How the forecast works": what the model looks at, how good it is, and that it stays on the phone. */
@Composable
fun ForecastInfoSheet(forecast: Forecast, onDismiss: () -> Unit, now: Long = System.currentTimeMillis()) {
    HabitlySheet(onDismiss = onDismiss, title = "How the forecast works") {
        ForecastInfoContent(forecast, now)
    }
}

@Composable
private fun ForecastInfoContent(forecast: Forecast, now: Long) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Body("A small model on your phone learns from your own check-ins which habits you tend to miss, and when.")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Heading("What it looks at")
            LOOKS_AT.forEach { line ->
                Row {
                    Body("•")
                    Spacer(Modifier.width(8.dp))
                    Body(line)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Heading("How good it is")
            val metrics = forecast.metrics
            if (metrics != null) {
                Body(metricsLine(metrics))
                metrics.baselineAuc?.let { Body("A simple guess (your usual rate) scores AUC ${twoDecimals(it)}") }
            } else {
                Body("Not enough history to measure yet")
            }
            Body(
                if (forecast.isLearning) {
                    "Using starter settings until there are ${RiskModel.MIN_ROWS} past check-ins"
                } else {
                    val updated = DateUtils.getRelativeTimeSpanString(forecast.trainedAt, now, DateUtils.MINUTE_IN_MILLIS)
                    "Trained on ${forecast.rows} past check-ins · updated $updated"
                },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Runs only on your phone. Nothing is sent anywhere.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

private fun metricsLine(m: ModelMetrics): String {
    val auc = m.auc?.let { " · AUC ${twoDecimals(it)}" }.orEmpty()
    val span = if (m.folds == 1) "week" else "${m.folds} weeks"
    return "Accuracy ${(m.accuracy * 100).roundToInt()}%$auc over the last $span (${m.testRows} check-ins)"
}

/** Fixed to a dot decimal so the AUC reads the same on every device locale. */
private fun twoDecimals(value: Double): String = String.format(Locale.US, "%.2f", value)

@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun Body(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun ForecastInfoPreviewContent(forecast: Forecast) {
    Box(Modifier.background(LocalHabitlyColors.current.surfaceRaised)) {
        HabitlySheetBody(title = "How the forecast works") { ForecastInfoContent(forecast, now = 3_600_000L) }
    }
}

private val previewTrained = previewForecast().copy(
    metrics = ModelMetrics(accuracy = 0.74, auc = 0.72, brier = 0.2, testRows = 64, baselineAuc = 0.63, folds = 4),
)

@Preview @Composable private fun ForecastInfoDarkPreview() = HabitlyTheme(darkTheme = true) { ForecastInfoPreviewContent(previewTrained) }
@Preview @Composable private fun ForecastInfoLightPreview() = HabitlyTheme(darkTheme = false) { ForecastInfoPreviewContent(previewTrained) }
@Preview @Composable private fun ForecastInfoLearningDarkPreview() = HabitlyTheme(darkTheme = true) { ForecastInfoPreviewContent(previewForecast(learning = true)) }
@Preview @Composable private fun ForecastInfoLearningLightPreview() = HabitlyTheme(darkTheme = false) { ForecastInfoPreviewContent(previewForecast(learning = true)) }
