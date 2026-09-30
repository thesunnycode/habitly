package com.habitly.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.habitly.app.ai.ui.AiSparkleIcon
import com.habitly.app.ml.Forecast
import com.habitly.app.ml.HabitRisk
import com.habitly.app.ml.RiskLevel
import com.habitly.app.ui.components.IconTile
import com.habitly.app.ui.theme.HabitlyTheme
import com.habitly.app.ui.theme.LocalHabitlyColors
import com.habitly.app.ui.theme.habitTones

private const val MAX_TILES = 3

/** Past this font scale three tiles can't fit side by side, so each takes its own row. */
private const val LARGE_FONT_SCALE = 1.3f

/** "Today's forecast" card: a headline for the riskiest habit and up to three risk tiles. */
@Composable
fun ForecastStrip(
    forecast: Forecast,
    onHabitClick: (Long) -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AiSparkleIcon(Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Today's forecast",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    IconButton(onClick = onInfo) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = "How the forecast works",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (forecast.isLearning) {
                    Text(
                        text = "Learning your patterns…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (forecast.allClear) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = LocalHabitlyColors.current.success)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "All clear for today ✓",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            } else {
                forecast.items.firstOrNull()?.let { top ->
                    Text(
                        text = headline(top),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                ForecastTiles(forecast.items.take(MAX_TILES), onHabitClick)
            }
        }
    }
}

private fun headline(top: HabitRisk): String = when (top.level) {
    RiskLevel.HIGH, RiskLevel.MEDIUM -> "${top.name} needs attention — ${top.reason}."
    RiskLevel.LOW -> "Looking good — ${top.reason}."
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ForecastTiles(items: List<HabitRisk>, onHabitClick: (Long) -> Unit) {
    val perRow = if (LocalDensity.current.fontScale > LARGE_FONT_SCALE) 1 else MAX_TILES
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = perRow,
    ) {
        items.forEach { risk ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(habitTones(risk.colorHex).low)
                    .semantics(mergeDescendants = true) {
                        contentDescription =
                            "${risk.name}: ${risk.percent}% chance of missing, ${risk.level.label} risk. ${risk.reason}"
                    }
                    .clickable(role = Role.Button, onClickLabel = "Open ${risk.name}") { onHabitClick(risk.habitId) }
                    .padding(12.dp),
            ) {
                IconTile(risk.iconKey, risk.colorHex, size = 28.dp)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "${risk.percent}%",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "chance of missing",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = risk.level.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

internal fun previewForecast(learning: Boolean = false, allClear: Boolean = false): Forecast = Forecast(
    items = if (allClear) emptyList() else listOf(
        HabitRisk(1, "Run", "run", "#FF922B", 0.72, RiskLevel.HIGH, "you often miss Mondays"),
        HabitRisk(2, "Read", "menu_book", "#9775FA", 0.41, RiskLevel.MEDIUM, "a patchy last week"),
        HabitRisk(3, "Water", "water_drop", "#4DABF7", 0.12, RiskLevel.LOW, "on a 6-day roll"),
    ),
    isLearning = learning,
    dueToday = 4,
    trainedAt = 0L,
    rows = if (learning) 0 else 212,
    metrics = null,
)

@Composable
private fun ForecastStripPreviewContent(forecast: Forecast) {
    Surface(color = MaterialTheme.colorScheme.background) {
        ForecastStrip(forecast, onHabitClick = {}, onInfo = {}, modifier = Modifier.padding(16.dp))
    }
}

@Preview @Composable private fun ForecastStripDarkPreview() = HabitlyTheme(darkTheme = true) { ForecastStripPreviewContent(previewForecast()) }
@Preview @Composable private fun ForecastStripLightPreview() = HabitlyTheme(darkTheme = false) { ForecastStripPreviewContent(previewForecast()) }
@Preview @Composable private fun ForecastStripLearningDarkPreview() = HabitlyTheme(darkTheme = true) { ForecastStripPreviewContent(previewForecast(learning = true)) }
@Preview @Composable private fun ForecastStripLearningLightPreview() = HabitlyTheme(darkTheme = false) { ForecastStripPreviewContent(previewForecast(learning = true)) }
@Preview @Composable private fun ForecastStripAllClearDarkPreview() = HabitlyTheme(darkTheme = true) { ForecastStripPreviewContent(previewForecast(allClear = true)) }
@Preview @Composable private fun ForecastStripAllClearLightPreview() = HabitlyTheme(darkTheme = false) { ForecastStripPreviewContent(previewForecast(allClear = true)) }
@Preview(fontScale = 2f) @Composable private fun ForecastStripLargeFontPreview() = HabitlyTheme(darkTheme = true) { ForecastStripPreviewContent(previewForecast()) }
