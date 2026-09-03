package com.wikiglobal.iconconverter.hyperos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedGlyphExtractorTest {
    @Test fun `edge connected background is removed while internal same color island remains`() {
        val bg = 0xffeeeeee.toInt(); val logo = 0xff222222.toInt(); val pixels = IntArray(64) { bg }
        for (y in 2..5) for (x in 2..5) pixels[y * 8 + x] = logo
        pixels[3 * 8 + 3] = bg // enclosed island: must not be flood-removed.
        val alpha = GeneratedGlyphExtractor.extractAlpha(pixels, 8, 8, BackgroundSegmentationConfig(colorDistance = .03f, minimumComponentPixels = 1))
        assertEquals(0, alpha[0]); assertTrue(alpha[3 * 8 + 3] > 0)
    }
    @Test fun `full bleed image remains unsafe`() {
        val alpha = GeneratedGlyphExtractor.extractAlpha(IntArray(64) { 0xff112233.toInt() }, 8, 8, BackgroundSegmentationConfig(minimumComponentPixels = 1))
        assertEquals(0, alpha.count { it > 0 })
    }
}
