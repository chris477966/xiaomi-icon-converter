package com.wikiglobal.iconconverter.hyperos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MonetThemeTest {
    private val palette = MonetPalette(1,2,7,8,20,30)
    @Test fun `light and dark mapping is explicit`() { assertEquals(1 to 7, palette.colors(false)); assertEquals(7 to 2, palette.colors(true)) }
    @Test fun `palette hash changes with palette`() { assertNotEquals(palette.hash(), palette.copy(accent1_700 = 9).hash()) }
    @Test fun `transparent centered glyph is accepted`() {
        val config = GlyphSafetyConfig(edgeInsetPx = 1)
        val alpha = IntArray(100) { i -> if (i / 10 in 3..6 && i % 10 in 3..6) 255 else 0 }
        assertEquals(null, GlyphSafetyAnalyzer.reject(GlyphSafetyAnalyzer.analyze(alpha, 10, 10, config), config))
    }
    @Test fun `full bleed opaque rectangle is rejected`() {
        val analysis = GlyphSafetyAnalyzer.analyze(IntArray(100) { 255 }, 10, 10)
        assertEquals(MonetGlyphSource.UNAVAILABLE_EXCESSIVE_COVERAGE, GlyphSafetyAnalyzer.reject(analysis))
    }
    @Test fun `opaque edges are rejected`() {
        val config = GlyphSafetyConfig(edgeInsetPx = 1)
        val alpha = IntArray(100) { i -> if (i % 10 == 0 || i % 10 == 1) 255 else 0 }
        assertEquals(MonetGlyphSource.UNAVAILABLE_OPAQUE_EDGES, GlyphSafetyAnalyzer.reject(GlyphSafetyAnalyzer.analyze(alpha, 10, 10, config), config))
    }
}
