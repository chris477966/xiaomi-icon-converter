package com.wikiglobal.iconconverter.hyperos

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import com.wikiglobal.iconconverter.renderer.IconRenderer
import java.security.MessageDigest

enum class MonetGlyphSource { NATIVE_MONOCHROME, ADAPTIVE_FOREGROUND, ICON_PACK_GLYPH, GENERATED_MASK, UNAVAILABLE }
data class MonetPalette(val accent100: Int, val accent200: Int, val accent700: Int, val accent800: Int, val accent2: Int?, val accent3: Int?) {
    fun colors(dark: Boolean) = if (dark) accent700 to accent200 else accent100 to accent700
    fun hash() = MessageDigest.getInstance("SHA-256").digest(listOf(accent100, accent200, accent700, accent800, accent2, accent3).joinToString().toByteArray()).joinToString("") { "%02x".format(it) }
}
object MonetPaletteReader {
    fun read(resources: Resources = Resources.getSystem()): MonetPalette? = runCatching {
        fun color(name: String): Int { val id = resources.getIdentifier(name, "color", "android"); require(id != 0) { "$name unavailable" }; return resources.getColor(id, null) }
        MonetPalette(color("system_accent1_100"), color("system_accent1_200"), color("system_accent1_700"), color("system_accent1_800"), color("system_accent2_100"), color("system_accent3_100"))
    }.getOrNull()
}
object MonochromeResolver {
    fun resolve(icon: Drawable, iconPackGlyph: Drawable? = null): Pair<Drawable?, MonetGlyphSource> = when (icon) {
        is AdaptiveIconDrawable -> if (android.os.Build.VERSION.SDK_INT >= 33 && icon.monochrome != null) icon.monochrome to MonetGlyphSource.NATIVE_MONOCHROME else icon.foreground to MonetGlyphSource.ADAPTIVE_FOREGROUND
        else -> iconPackGlyph?.let { it to MonetGlyphSource.ICON_PACK_GLYPH } ?: (null to MonetGlyphSource.UNAVAILABLE)
    }
}
object MonetGlyphRenderer {
    fun render(glyph: Drawable, palette: MonetPalette, dark: Boolean): ByteArray {
        val (background, foreground) = palette.colors(dark); val size = HyperOs3ThemePatcher.ICON_SIZE
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888); val canvas = Canvas(bitmap); val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background }
        canvas.drawRoundRect(12f, 12f, 238f, 238f, 58f, 58f, paint)
        glyph.setTint(foreground); glyph.setBounds(Rect(42, 42, 208, 208)); glyph.draw(canvas)
        return IconRenderer.bitmapToPng(bitmap)
    }
}
