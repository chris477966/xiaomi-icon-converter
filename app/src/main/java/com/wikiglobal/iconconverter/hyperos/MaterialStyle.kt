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
            MaterialIconShape.ROUNDED_SQUARE -> canvas.drawRoundRect(bounds, roundedSquareRadius(bounds), roundedSquareRadius(bounds), paint)
            MaterialIconShape.SQUIRCLE -> canvas.drawPath(squircle(bounds), paint)
        }
    }
    data class CubicCorner(val startX:Float,val startY:Float,val control1X:Float,val control1Y:Float,val control2X:Float,val control2Y:Float,val endX:Float,val endY:Float)
    fun roundedSquareRadius(rect:RectF)=roundedSquareRadius(rect.width(),rect.height())
    fun roundedSquareRadius(width:Float,height:Float)=minOf(width,height)/2f*LAWNCHAIR_ROUNDED_SQUARE_SCALE
    /** BaseBezierPath: mapRange(.2, control=(1,0), start=(0,0)) and end=(1,1), then scale by cornerSize. */
    fun topRightSquircleCorner(rect:RectF)=topRightSquircleCorner(rect.left,rect.top,rect.right,rect.bottom)
    fun topRightSquircleCorner(left:Float,top:Float,right:Float,bottom:Float):CubicCorner { val size=minOf(right-left,bottom-top)/2f;val offset=size*LAWNCHAIR_SQUIRCLE_CONTROL_DISTANCE;return CubicCorner(right-size,top,right-offset,top,right,top+offset,right,top+size) }
    /** Lawnchair Squircle BaseBezierPath with cornerSize=min(bounds)/2 and controlDistance=.2. */
    fun squircle(rect: RectF): Path {
        val tr=topRightSquircleCorner(rect);val size=minOf(rect.width(),rect.height())/2f;val offset=size*LAWNCHAIR_SQUIRCLE_CONTROL_DISTANCE
        return Path().apply {
            moveTo(tr.startX,tr.startY);cubicTo(tr.control1X,tr.control1Y,tr.control2X,tr.control2Y,tr.endX,tr.endY)
            cubicTo(rect.right,rect.bottom-offset,rect.right-offset,rect.bottom,rect.right-size,rect.bottom)
            cubicTo(rect.left+offset,rect.bottom,rect.left,rect.bottom-offset,rect.left,rect.bottom-size)
            cubicTo(rect.left,rect.top+offset,rect.left+offset,rect.top,rect.left+size,rect.top);close()
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
