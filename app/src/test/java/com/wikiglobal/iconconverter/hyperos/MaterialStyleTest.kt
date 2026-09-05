package com.wikiglobal.iconconverter.hyperos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialStyleTest {
    @Test fun `three color modes are available and new style defaults to wallpaper auto`() {
        assertEquals(3, MaterialColorMode.entries.size)
        assertEquals(MaterialColorMode.WALLPAPER_AUTO, MaterialStyle().colorMode)
    }
    @Test fun `system Monet mode preserves system palette`() {
        val system = MonetPalette(1, 2, 7, 8, 20, 30)
        assertEquals(system, MaterialPaletteFactory.forStyle(MaterialStyle(colorMode = MaterialColorMode.SYSTEM_MONET), system))
    }
    @Test fun `custom seed uses tonal palette contrast`() {
        val palette = MaterialPaletteFactory.forStyle(MaterialStyle(colorMode = MaterialColorMode.CUSTOM, customSeedColor = 0xff336699.toInt()), null)!!
        assertNotEquals(palette.accent100, palette.accent700)
        assertNotEquals(palette.colors(false).first, palette.colors(false).second)
    }
    @Test fun `wallpaper and custom use Lawnchair Tonal Spot roles`() {
        assertEquals(36.0, AospMonetPaletteFactory.TONAL_SPOT_A1_CHROMA, 0.0)
        assertEquals(16.0, AospMonetPaletteFactory.TONAL_SPOT_A2_CHROMA, 0.0)
        assertEquals(24.0, AospMonetPaletteFactory.TONAL_SPOT_A3_CHROMA, 0.0)
        assertEquals(60.0, AospMonetPaletteFactory.TONAL_SPOT_A3_HUE_OFFSET, 0.0)
        assertEquals(6.0, AospMonetPaletteFactory.TONAL_SPOT_N1_CHROMA, 0.0)
        assertEquals(8.0, AospMonetPaletteFactory.TONAL_SPOT_N2_CHROMA, 0.0)
        val seed = 0xff336699.toInt()
        val actual = MaterialPaletteFactory.fromSeed(seed)
        assertEquals(AospMonetPaletteFactory.tonalSpot(seed), actual)
        assertEquals(actual.accent1_100 to actual.accent1_700, actual.colors(false))
        assertEquals(actual.accent2_800 to actual.accent1_200, actual.colors(true))
    }
    @Test fun `custom mode cannot follow wallpaper`() {
        val style = MaterialStyle(colorMode = MaterialColorMode.CUSTOM, followWallpaperMonet = true)
        assertTrue(!style.normalized().followWallpaperMonet)
    }
    @Test fun `HyperOS shape baseline bounds remain unchanged`() {
        assertEquals(12f, MaterialIconShapeRenderer.boundsValues[0])
        assertEquals(238f, MaterialIconShapeRenderer.boundsValues[2])
    }
    @Test fun `squircle reference points use Lawnchair BaseBezier mapping`() {
        val c=MaterialIconShapeRenderer.topRightSquircleCorner(12f,12f,238f,238f)
        assertEquals(125f,c.startX);assertEquals(215.4f,c.control1X,.001f);assertEquals(238f,c.control2X);assertEquals(34.6f,c.control2Y,.001f);assertEquals(125f,c.endY)
    }
    @Test fun `circle and squircle geometry are not equal`() {
        val c=MaterialIconShapeRenderer.topRightSquircleCorner(12f,12f,238f,238f)
        val circleControl=238f-113f*(1f-.551915f)
        assertNotEquals(circleControl,c.control1X)
    }
    @Test fun `rounded square radius follows Lawnchair scale`() { assertEquals(67.8f,MaterialIconShapeRenderer.roundedSquareRadius(226f,226f),.001f) }
    @Test fun `shape changes do not alter source maps`() {
        val sources = mapOf("p#A" to MonetGlyphSource.LAWNICONS_PACKAGE)
        assertEquals(sources, sources)
    }
}
