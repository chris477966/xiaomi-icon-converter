package com.wikiglobal.iconconverter.hyperos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialStyleTest {
    @Test fun `system Monet mode preserves system palette`() {
        val system = MonetPalette(1, 2, 7, 8, 20, 30)
        assertEquals(system, MaterialPaletteFactory.forStyle(MaterialStyle(colorMode = MaterialColorMode.SYSTEM_MONET), system))
    }
    @Test fun `custom seed uses tonal palette contrast`() {
        val palette = MaterialPaletteFactory.forStyle(MaterialStyle(colorMode = MaterialColorMode.CUSTOM, customSeedColor = 0xff336699.toInt()), null)!!
        assertNotEquals(palette.accent100, palette.accent700)
        assertNotEquals(palette.colors(false).first, palette.colors(false).second)
    }
    @Test fun `custom mode cannot follow wallpaper`() {
        val style = MaterialStyle(colorMode = MaterialColorMode.CUSTOM, followWallpaperMonet = true)
        assertTrue(!style.normalized().followWallpaperMonet)
    }
    @Test fun `HyperOS shape baseline bounds remain unchanged`() {
        assertEquals(12f, MaterialIconShapeRenderer.boundsValues[0])
        assertEquals(238f, MaterialIconShapeRenderer.boundsValues[2])
    }
    @Test fun `shape changes do not alter source maps`() {
        val sources = mapOf("p#A" to MonetGlyphSource.LAWNICONS_PACKAGE)
        assertEquals(sources, sources)
    }
}
