package com.wikiglobal.iconconverter.hyperos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.TonalPalette
import com.wikiglobal.iconconverter.renderer.IconRenderer

enum class MaterialColorMode { SYSTEM_MONET, CUSTOM }
enum class MaterialIconShape { HYPEROS, CIRCLE, SQUIRCLE, ROUNDED_SQUARE }
data class MaterialStyle(val colorMode: MaterialColorMode = MaterialColorMode.SYSTEM_MONET, val customSeedColor: Int = Color.BLUE, val shape: MaterialIconShape = MaterialIconShape.HYPEROS, val followWallpaperMonet: Boolean = false) { fun normalized() = if (colorMode == MaterialColorMode.CUSTOM) copy(followWallpaperMonet = false) else this }

class MaterialStyleStore(context: Context) {
    private val prefs = context.getSharedPreferences("material-style", Context.MODE_PRIVATE)
    fun get() = MaterialStyle(
        runCatching { MaterialColorMode.valueOf(prefs.getString("colorMode", MaterialColorMode.SYSTEM_MONET.name)!!) }.getOrDefault(MaterialColorMode.SYSTEM_MONET),
        prefs.getInt("seed", Color.BLUE),
        runCatching { MaterialIconShape.valueOf(prefs.getString("shape", MaterialIconShape.HYPEROS.name)!!) }.getOrDefault(MaterialIconShape.HYPEROS),
        prefs.getBoolean("follow", false)
    )
    fun set(style: MaterialStyle) { val normalized=style.normalized(); prefs.edit().putString("colorMode", normalized.colorMode.name).putInt("seed", normalized.customSeedColor).putString("shape", normalized.shape.name).putBoolean("follow", normalized.followWallpaperMonet).apply() }
}

/** Uses official Material Color Utilities HCT tonal palettes; no HSL approximation is used. */
object MaterialPaletteFactory {
    fun forStyle(style: MaterialStyle, system: MonetPalette?): MonetPalette? = when (style.colorMode) {
        MaterialColorMode.SYSTEM_MONET -> system
        MaterialColorMode.CUSTOM -> custom(style.customSeedColor)
    }
    fun custom(seed: Int): MonetPalette {
        val hct = Hct.fromInt(seed)
        val accent1 = TonalPalette.fromHueAndChroma(hct.hue, maxOf(48.0, hct.chroma))
        val accent2 = TonalPalette.fromHueAndChroma(hct.hue, maxOf(16.0, hct.chroma / 3))
        return MonetPalette(accent1.tone(90), accent1.tone(80), accent1.tone(30), accent1.tone(20), accent2.tone(80), accent2.tone(80))
    }
}

/** Lawnchair-inspired geometric masks rendered into a static 250px HyperOS PNG. */
object MaterialIconShapeRenderer {
    const val LAWNCHAIR_SQUIRCLE_CONTROL_DISTANCE = .2f
    const val LAWNCHAIR_ROUNDED_SQUARE_SCALE = .6f
    val boundsValues = floatArrayOf(12f, 12f, 238f, 238f)
    val bounds = RectF(boundsValues[0], boundsValues[1], boundsValues[2], boundsValues[3])
    fun drawBackground(canvas: Canvas, shape: MaterialIconShape, paint: Paint) {
        when (shape) {
            MaterialIconShape.HYPEROS -> canvas.drawRoundRect(bounds, 58f, 58f, paint)
            MaterialIconShape.CIRCLE -> canvas.drawOval(bounds, paint)
            MaterialIconShape.ROUNDED_SQUARE -> canvas.drawRoundRect(bounds, bounds.width() * (1f - LAWNCHAIR_ROUNDED_SQUARE_SCALE) / 2f, bounds.width() * (1f - LAWNCHAIR_ROUNDED_SQUARE_SCALE) / 2f, paint)
            MaterialIconShape.SQUIRCLE -> canvas.drawPath(squircle(bounds), paint)
        }
    }
    /** Lawnchair Squircle's normalized 0.2 control distance, transformed into HyperOS bounds. */
    fun squircle(rect: RectF): Path {
        val cx = rect.centerX(); val cy = rect.centerY(); val dx=rect.width()*LAWNCHAIR_SQUIRCLE_CONTROL_DISTANCE;val dy=rect.height()*LAWNCHAIR_SQUIRCLE_CONTROL_DISTANCE
        return Path().apply {
            moveTo(cx, rect.top); cubicTo(rect.right-dx,rect.top,rect.right,cy-dy,rect.right,cy)
            cubicTo(rect.right,cy+dy,rect.right-dx,rect.bottom,cx,rect.bottom)
            cubicTo(rect.left+dx,rect.bottom,rect.left,cy+dy,rect.left,cy)
            cubicTo(rect.left,cy-dy,rect.left+dx,rect.top,cx,rect.top); close()
        }
    }
}

/** Style-aware renderer intentionally leaves the v0.2.8 MonetGlyphRenderer untouched. */
object MaterialStyledGlyphRenderer {
    fun render(result: MonetGlyphResult, palette: MonetPalette, dark: Boolean, shape: MaterialIconShape): ByteArray? {
        val mask = result.alphaMask ?: return null; val (background, foreground) = palette.colors(dark)
        val bitmap = Bitmap.createBitmap(HyperOs3ThemePatcher.ICON_SIZE, HyperOs3ThemePatcher.ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        MaterialIconShapeRenderer.drawBackground(canvas, shape, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background })
        canvas.drawBitmap(mask, null, Rect(42, 42, 208, 208), Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = PorterDuffColorFilter(foreground, PorterDuff.Mode.SRC_IN) })
        return if (MonetOutputValidator.validate(bitmap, foreground)) IconRenderer.bitmapToPng(bitmap) else null
    }
}
