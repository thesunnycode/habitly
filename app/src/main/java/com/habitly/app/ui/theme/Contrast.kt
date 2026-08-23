package com.habitly.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/**
 * WCAG contrast ratio between two opaque colours, from 1.0 (identical) to 21.0 (black on white).
 */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
}

/**
 * Nudges a habit's own colour until it is legible as text or as a meaningful graphic against
 * [background].
 *
 * Habit colours are picked once from a palette and then reused in both themes, which is how a
 * cheerful pastel ends up as unreadable label text on a light surface — and how the same hex ends up
 * too dark to see on a dark one. Rather than forbidding colours, this walks the colour toward black
 * or white (whichever direction the background needs) only as far as it must to clear [target].
 *
 * @param target 4.5 for text, 3.0 for icons and other non-text graphics, per WCAG 1.4.3 / 1.4.11.
 */
fun Color.readableOn(background: Color, target: Float = 4.5f): Color {
    if (contrastRatio(this, background) >= target) return this

    // Darken against a light background, lighten against a dark one.
    val towards = if (background.luminance() > 0.5f) Color.Black else Color.White

    var low = 0f
    var high = 1f
    var best = lerp(this, towards, 1f)
    // Binary search for the smallest shift that clears the target, so the colour stays recognisable.
    repeat(12) {
        val mid = (low + high) / 2f
        val candidate = lerp(this, towards, mid)
        if (contrastRatio(candidate, background) >= target) {
            best = candidate
            high = mid
        } else {
            low = mid
        }
    }
    return best
}
