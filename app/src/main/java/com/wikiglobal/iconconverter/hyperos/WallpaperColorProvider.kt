package com.wikiglobal.iconconverter.hyperos

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

data class WallpaperColorState(
    val primary: Int?,
    val secondary: Int?,
    val tertiary: Int?,
    val hash: String
)

/** Reads only WallpaperColors; it never obtains a wallpaper Drawable or bitmap. */
class WallpaperColorProvider(private val context: Context) {
    suspend fun read(): WallpaperColorState? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@withContext null
        runCatching {
            val colors = WallpaperManager.getInstance(context)
                .getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return@runCatching null
            val primary = colors.primaryColor?.toArgb()
            val secondary = colors.secondaryColor?.toArgb()
            val tertiary = colors.tertiaryColor?.toArgb()
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(listOf(primary, secondary, tertiary).joinToString().toByteArray())
                .joinToString("") { "%02x".format(it) }
            WallpaperColorState(primary, secondary, tertiary, hash)
        }.getOrNull()
    }
}

enum class MaterialPaletteSource { WALLPAPER, SYSTEM_MONET, CUSTOM }

data class MaterialPaletteResolution(
    val palette: MonetPalette,
    val source: MaterialPaletteSource,
    val seedColor: Int? = null,
    val wallpaperColors: WallpaperColorState? = null,
    val fallbackUsed: Boolean = false
) {
    val hash: String get() = palette.hash()
}

fun interface WallpaperColorSource { suspend fun read(): WallpaperColorState? }

/** One resolver is shared by preview, foreground refresh and automatic recolor. */
class MaterialPaletteResolver(
    private val wallpaperSource: WallpaperColorSource,
    private val systemSource: () -> MonetPalette? = { MonetPaletteReader.read() }
) {
    constructor(context: Context) : this(WallpaperColorSource { WallpaperColorProvider(context).read() })

    suspend fun resolve(style: MaterialStyle): MaterialPaletteResolution? = when (style.colorMode) {
        MaterialColorMode.WALLPAPER_AUTO -> {
            val colors = wallpaperSource.read()
            val primary = colors?.primary
            if (primary != null) {
                MaterialPaletteResolution(MaterialPaletteFactory.fromSeed(primary), MaterialPaletteSource.WALLPAPER, primary, colors)
            } else {
                systemSource()?.let { MaterialPaletteResolution(it, MaterialPaletteSource.SYSTEM_MONET, fallbackUsed = true) }
            }
        }
        MaterialColorMode.SYSTEM_MONET -> systemSource()?.let { MaterialPaletteResolution(it, MaterialPaletteSource.SYSTEM_MONET) }
        MaterialColorMode.CUSTOM -> MaterialPaletteResolution(MaterialPaletteFactory.fromSeed(style.customSeedColor), MaterialPaletteSource.CUSTOM, style.customSeedColor)
    }
}
