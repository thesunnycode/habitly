package com.habitly.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.habitly.app.ui.components.HABIT_COLOR_SWATCHES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsed in pure Kotlin on purpose: android.graphics.Color.parseColor returns 0 under a plain JVM
 * test, which would quietly turn every swatch into transparent black and make these assertions lie.
 */
private fun hex(value: String): Color {
    val rgb = value.removePrefix("#").toLong(16)
    return Color(0xFF000000L.or(rgb).toULong().shl(32))
}

class ContrastTest {

    private val lightSurface = Color(0xFFFAFAF8)
    private val darkSurface = Color(0xFF121212)

    @Test
    fun `black on white is the maximum ratio`() {
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.1f)
    }

    @Test
    fun `a colour against itself has no contrast`() {
        assertEquals(1f, contrastRatio(hex("#6B9080"), hex("#6B9080")), 0.01f)
    }

    @Test
    fun `a colour that already passes is returned untouched`() {
        val dark = hex("#495057")
        assertEquals(dark, dark.readableOn(lightSurface, target = 3f))
    }

    @Test
    fun `a pale accent is darkened until it is readable on a light surface`() {
        val pale = hex("#F6BD60")
        val fixed = pale.readableOn(lightSurface, target = 4.5f)
        assertTrue(contrastRatio(fixed, lightSurface) >= 4.5f)
        assertTrue("should darken, not lighten", fixed.luminanceValue() < pale.luminanceValue())
    }

    @Test
    fun `a deep accent is lightened until it is readable on a dark surface`() {
        val deep = hex("#4C6EF5")
        val fixed = deep.readableOn(darkSurface, target = 4.5f)
        assertTrue(contrastRatio(fixed, darkSurface) >= 4.5f)
        assertTrue("should lighten, not darken", fixed.luminanceValue() > deep.luminanceValue())
    }

    @Test
    fun `every palette swatch can be made readable in both themes`() {
        HABIT_COLOR_SWATCHES.forEach { swatch ->
            val color = hex(swatch)
            assertTrue(
                "$swatch fails on light",
                contrastRatio(color.readableOn(lightSurface), lightSurface) >= 4.5f,
            )
            assertTrue(
                "$swatch fails on dark",
                contrastRatio(color.readableOn(darkSurface), darkSurface) >= 4.5f,
            )
        }
    }

    @Test
    fun `the graphics target is easier to meet than the text target`() {
        val swatch = hex("#1098AD")
        val forGraphics = swatch.readableOn(lightSurface, target = 3f)
        val forText = swatch.readableOn(lightSurface, target = 4.5f)
        assertTrue(forGraphics.luminanceValue() >= forText.luminanceValue())
    }
}

private fun Color.luminanceValue(): Float = this.luminance()
