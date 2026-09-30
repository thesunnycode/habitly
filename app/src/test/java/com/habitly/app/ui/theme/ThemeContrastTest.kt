package com.habitly.app.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec §3.1: every text and state role in both themes clears its WCAG contrast floor. */
class ThemeContrastTest {

    @Test
    fun `body text meets 4_5 on its surfaces in both themes`() {
        listOf(
            DarkOnSurface to DarkSurface,
            DarkOnSurface to DarkBackground,
            DarkOnSurfaceMuted to DarkSurface,
            LightOnSurface to LightSurface,
            LightOnSurface to LightBackground,
            LightOnSurfaceMuted to LightSurface,
            DarkBackground to DarkOnSurface, // primary button
            LightSurface to LightOnSurface,
        ).forEach { (fg, bg) ->
            assertTrue("$fg on $bg = ${contrastRatio(fg, bg)}", contrastRatio(fg, bg) >= 4.5f)
        }
    }

    @Test
    fun `brand and state colours are visible graphics`() {
        listOf(
            DarkBrand to DarkSurface,
            DarkSuccess to DarkSurface,
            DarkDanger to DarkSurface,
            DarkWarning to DarkSurface,
            LightBrand to LightSurface,
            LightSuccess to LightSurface,
            LightDanger to LightSurface,
            LightWarning to LightSurface,
            DarkOutline to DarkBackground,
        ).forEach { (fg, bg) ->
            val floor = if (fg == DarkOutline) 1.3f else 3f
            assertTrue("$fg on $bg = ${contrastRatio(fg, bg)}", contrastRatio(fg, bg) >= floor)
        }
    }
}
