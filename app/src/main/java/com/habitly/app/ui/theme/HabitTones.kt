package com.habitly.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Parses "#RRGGBB" in pure Kotlin — android.graphics.Color returns 0 under JVM tests. */
fun hexToColor(hex: String): Color {
    val rgb = hex.removePrefix("#").toLong(16)
    return Color(0xFF000000L or rgb)
}

object HabitPalette {
    val seeds = listOf(
        "#FF922B", "#4DABF7", "#9775FA", "#51CF66", "#FF6B6B", "#FCC419",
        "#22B8CF", "#F06595", "#94D82D", "#748FFC", "#FF8787", "#CED4DA",
    )
    private const val GREY = "#CED4DA"

    /** Colours saved before the redesign are shown as the closest seed by hue; greys stay grey. */
    fun nearestSeed(hex: String): String {
        val upper = hex.uppercase()
        if (upper in seeds) return upper
        val (h, s, _) = hsl(hexToColor(hex))
        if (s < 0.18f) return GREY
        return seeds.filter { it != GREY && it != "#FF8787" }
            .minBy { seed -> hueDistance(h, hsl(hexToColor(seed)).first) }
    }

    private fun hueDistance(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }
}

@Immutable
data class HabitTones(val full: Color, val mid: Color, val low: Color, val onFull: Color)

object HabitTonesFactory {
    private val ink = Color(0xFF0E0F12)
    private val white = Color.White

    /** Spec §3.2 — lightness is varied, never alpha, so every step stays perceivable. */
    fun of(hex: String, isDark: Boolean): HabitTones {
        val seed = hexToColor(HabitPalette.nearestSeed(hex))
        return if (isDark) {
            val (h, s, l) = hsl(seed)
            HabitTones(
                full = seed,
                mid = hslToColor(h, s, l * 0.72f),
                low = hslToColor(h, s * 0.75f, l * 0.36f),
                onFull = ink,
            )
        } else {
            val full = seed.readableOn(white, target = 3f)
            HabitTones(
                full = full,
                mid = lerp(white, full, 0.6f),
                low = lerp(white, full, 0.25f),
                onFull = if (contrastRatio(white, full) >= 3f) white else LightOnSurface,
            )
        }
    }
}

@Composable
fun habitTones(hex: String): HabitTones = HabitTonesFactory.of(hex, LocalHabitlyColors.current.isDark)

internal fun hsl(c: Color): Triple<Float, Float, Float> {
    val r = c.red; val g = c.green; val b = c.blue
    val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
    val l = (mx + mn) / 2f
    if (mx == mn) return Triple(0f, 0f, l)
    val d = mx - mn
    val s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
    val h = when (mx) {
        r -> ((g - b) / d + (if (g < b) 6f else 0f))
        g -> ((b - r) / d + 2f)
        else -> ((r - g) / d + 4f)
    } * 60f
    return Triple(h, s, l)
}

internal fun hslToColor(h: Float, s: Float, l: Float): Color {
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r, g, b) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        (r + m).coerceIn(0f, 1f), (g + m).coerceIn(0f, 1f), (b + m).coerceIn(0f, 1f),
    )
}
