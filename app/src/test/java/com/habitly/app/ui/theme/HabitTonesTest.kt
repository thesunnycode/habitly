package com.habitly.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitTonesTest {

    private val darkCard = Color(0xFF17191E)
    private val lightCard = Color(0xFFFFFFFF)

    @Test
    fun `there are twelve palette seeds`() {
        assertEquals(12, HabitPalette.seeds.size)
    }

    @Test
    fun `every seed's full tone is a visible graphic on the dark card`() {
        HabitPalette.seeds.forEach { seed ->
            val tones = HabitTonesFactory.of(seed, isDark = true)
            assertTrue("$seed full on dark", contrastRatio(tones.full, darkCard) >= 3f)
        }
    }

    @Test
    fun `every seed's full tone is a visible graphic on the light card`() {
        HabitPalette.seeds.forEach { seed ->
            val tones = HabitTonesFactory.of(seed, isDark = false)
            assertTrue("$seed full on light", contrastRatio(tones.full, lightCard) >= 3f)
        }
    }

    @Test
    fun `the check glyph is legible on the full tone in both themes`() {
        HabitPalette.seeds.forEach { seed ->
            listOf(true, false).forEach { dark ->
                val t = HabitTonesFactory.of(seed, dark)
                assertTrue("$seed onFull dark=$dark", contrastRatio(t.onFull, t.full) >= 3f)
            }
        }
    }

    @Test
    fun `the three fill tones are distinct steps`() {
        HabitPalette.seeds.forEach { seed ->
            listOf(true, false).forEach { dark ->
                val t = HabitTonesFactory.of(seed, dark)
                assertNotEquals(t.full, t.mid)
                assertNotEquals(t.mid, t.low)
            }
        }
    }

    @Test
    fun `dark tones step down in luminance`() {
        HabitPalette.seeds.forEach { seed ->
            val t = HabitTonesFactory.of(seed, isDark = true)
            assertTrue(t.full.luminanceValue() > t.mid.luminanceValue())
            assertTrue(t.mid.luminanceValue() > t.low.luminanceValue())
        }
    }

    @Test
    fun `legacy colours map to the nearest seed by hue`() {
        assertEquals("#22B8CF", HabitPalette.nearestSeed("#1098AD")) // old teal -> cyan
        assertEquals("#FF6B6B", HabitPalette.nearestSeed("#F76C5E")) // old coral -> red
        assertEquals("#CED4DA", HabitPalette.nearestSeed("#495057")) // grey stays grey
        assertEquals("#FF922B", HabitPalette.nearestSeed("#FF922B")) // a seed maps to itself
    }

    @Test
    fun `hexToColor parses without android framework`() {
        assertEquals(Color(0xFF4DABF7), hexToColor("#4DABF7"))
    }
}

private fun Color.luminanceValue() = this.luminance()
