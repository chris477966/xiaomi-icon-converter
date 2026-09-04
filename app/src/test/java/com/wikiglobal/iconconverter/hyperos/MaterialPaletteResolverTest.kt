package com.wikiglobal.iconconverter.hyperos

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialPaletteResolverTest {
    private val system = MonetPalette(1, 2, 7, 8, 20, 30)

    @Test fun `wallpaper primary is the only seed for wallpaper auto`() = runBlocking {
        val colors = WallpaperColorState(0xff336699.toInt(), 0xffaa0000.toInt(), 0xff00aa00.toInt(), "wallpaper")
        val result = MaterialPaletteResolver(WallpaperColorSource { colors }) { system }
            .resolve(MaterialStyle(MaterialColorMode.WALLPAPER_AUTO))!!
        assertEquals(MaterialPaletteSource.WALLPAPER, result.source)
        assertEquals(colors, result.wallpaperColors)
        assertEquals(colors.primary, result.seedColor)
        assertFalse(result.fallbackUsed)
        assertEquals(MaterialPaletteFactory.fromSeed(colors.primary!!), result.palette)
        assertEquals(result.palette.hash(), result.renderHash)
        assertEquals(colors.hash, result.wallpaperStateHash)
    }

    @Test fun `wallpaper unavailable falls back to system palette`() = runBlocking {
        val result = MaterialPaletteResolver(WallpaperColorSource { null }) { system }
            .resolve(MaterialStyle(MaterialColorMode.WALLPAPER_AUTO))!!
        assertEquals(MaterialPaletteSource.SYSTEM_MONET, result.source)
        assertEquals(system, result.palette)
        assertTrue(result.fallbackUsed)
    }

    @Test fun `system and custom modes use the same resolver`() = runBlocking {
        val resolver = MaterialPaletteResolver(WallpaperColorSource { error("must not read") }) { system }
        assertEquals(system, resolver.resolve(MaterialStyle(MaterialColorMode.SYSTEM_MONET))!!.palette)
        val custom = resolver.resolve(MaterialStyle(MaterialColorMode.CUSTOM, customSeedColor = 0xff336699.toInt()))!!
        assertEquals(MaterialPaletteSource.CUSTOM, custom.source)
        assertEquals(MaterialPaletteFactory.fromSeed(0xff336699.toInt()), custom.palette)
    }
}
