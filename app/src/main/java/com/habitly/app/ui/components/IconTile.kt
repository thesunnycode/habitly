package com.habitly.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.habitly.app.ui.theme.HabitlyTheme
import com.habitly.app.ui.theme.habitTones

/** A habit's icon on its tinted tile: `low` background, `full` glyph. */
@Composable
fun IconTile(
    iconKey: String,
    colorHex: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    val tones = habitTones(colorHex)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(tones.low),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = habitIconFor(iconKey),
            contentDescription = null,
            tint = tones.full,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

@Composable
private fun IconTilePreviewContent() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile("run", "#FF922B")
            IconTile("water_drop", "#4DABF7", size = 48.dp)
            IconTile("menu_book", "#9775FA", size = 28.dp)
        }
    }
}

@Preview @Composable private fun IconTileDarkPreview() = HabitlyTheme(darkTheme = true) { IconTilePreviewContent() }
@Preview @Composable private fun IconTileLightPreview() = HabitlyTheme(darkTheme = false) { IconTilePreviewContent() }
