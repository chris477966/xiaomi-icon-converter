package com.wikiglobal.iconconverter.hyperos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import com.wikiglobal.iconconverter.renderer.IconRenderer

enum class MaterialColorMode { WALLPAPER_AUTO, SYSTEM_MONET, CUSTOM }
/** Source-compatible name for v0.2.9 callers; all persistence uses shared IconShape. */
typealias MaterialIconShape = IconShape
data class MaterialStyle(val colorMode: MaterialColorMode = MaterialColorMode.WALLPAPER_AUTO, val customSeedColor: Int = Color.BLUE, val shape: IconShape = IconShape.HYPEROS, val followWallpaperMonet: Boolean = false) {
    /** New API name; the old property remains as the persisted compatibility field. */
    val autoApplyWallpaperChanges: Boolean get() = followWallpaperMonet
    fun normalized() = if (colorMode == MaterialColorMode.CUSTOM) copy(followWallpaperMonet = false) else this
}

class MaterialStyleStore(context: Context) {
    private val prefs = context.getSharedPreferences("material-style", Context.MODE_PRIVATE)
    fun get() = MaterialStyle(
        runCatching { MaterialColorMode.valueOf(prefs.getString("colorMode", MaterialColorMode.WALLPAPER_AUTO.name)!!) }.getOrDefault(MaterialColorMode.WALLPAPER_AUTO),
        prefs.getInt("seed", Color.BLUE),
        runCatching { IconShape.valueOf(prefs.getString("shape", IconShape.HYPEROS.name)!!) }.getOrDefault(IconShape.HYPEROS),
        prefs.getBoolean("follow", false)
    )
    fun set(style: MaterialStyle) { val normalized=style.normalized(); prefs.edit().putString("colorMode", normalized.colorMode.name).putInt("seed", normalized.customSeedColor).putString("shape", normalized.shape.name).putBoolean("follow", normalized.followWallpaperMonet).apply() }
}

/** Lawnchair/AOSP Tonal Spot palette: fixed role chroma, with the wallpaper/custom seed as hue source. */
object MaterialPaletteFactory {
    fun forStyle(style: MaterialStyle, system: MonetPalette?): MonetPalette? = when (style.colorMode) {
        MaterialColorMode.WALLPAPER_AUTO -> null
        MaterialColorMode.SYSTEM_MONET -> system
        MaterialColorMode.CUSTOM -> fromSeed(style.customSeedColor)
    }
    fun fromSeed(seed: Int): MonetPalette = AospMonetPaletteFactory.tonalSpot(seed)
    fun custom(seed: Int): MonetPalette = fromSeed(seed)
}

/** Lawnchair-inspired geometric masks rendered into a static 250px HyperOS PNG. */
object MaterialIconShapeRenderer {
    const val LAWNCHAIR_SQUIRCLE_CONTROL_DISTANCE = IconShapePathFactory.LAWNCHAIR_SQUIRCLE_CONTROL_DISTANCE
    const val LAWNCHAIR_ROUNDED_SQUARE_SCALE = IconShapePathFactory.LAWNCHAIR_ROUNDED_SQUARE_SCALE
    val boundsValues = floatArrayOf(12f, 12f, 238f, 238f)
    val bounds = IconShapePathFactory.canonicalBounds
    fun drawBackground(canvas: Canvas, shape: IconShape, paint: Paint) = canvas.drawPath(IconShapePathFactory.path(shape), paint)
    /** Lightweight preview using the exact production geometry and bounds. */
    fun previewBitmap(shape: IconShape, color: Int, size: Int = 56): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.save()
        canvas.scale(size / IconShapePathFactory.CANONICAL_SIZE, size / IconShapePathFactory.CANONICAL_SIZE)
        drawBackground(canvas, shape, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        canvas.restore()
        return bitmap
    }
    data class CubicCorner(val startX:Float,val startY:Float,val control1X:Float,val control1Y:Float,val control2X:Float,val control2Y:Float,val endX:Float,val endY:Float)
    fun roundedSquareRadius(rect:android.graphics.RectF)=roundedSquareRadius(rect.width(),rect.height())
    fun roundedSquareRadius(width:Float,height:Float)=minOf(width,height)/2f*LAWNCHAIR_ROUNDED_SQUARE_SCALE
    /** BaseBezierPath: mapRange(.2, control=(1,0), start=(0,0)) and end=(1,1), then scale by cornerSize. */
    fun topRightSquircleCorner(rect:android.graphics.RectF)=topRightSquircleCorner(rect.left,rect.top,rect.right,rect.bottom)
    fun topRightSquircleCorner(left:Float,top:Float,right:Float,bottom:Float):CubicCorner { val size=minOf(right-left,bottom-top)/2f;val offset=size*LAWNCHAIR_SQUIRCLE_CONTROL_DISTANCE;return CubicCorner(right-size,top,right-offset,top,right,top+offset,right,top+size) }
    /** Lawnchair Squircle BaseBezierPath with cornerSize=min(bounds)/2 and controlDistance=.2. */
    fun squircle(rect: android.graphics.RectF) = IconShapePathFactory.squircle(rect)
}

/** Style-aware renderer intentionally leaves the v0.2.8 MonetGlyphRenderer untouched. */
object MaterialStyledGlyphRenderer {
    fun render(result: MonetGlyphResult, palette: MonetPalette, dark: Boolean, shape: IconShape, targetSize: Int = HyperOsThemeProfileDetector.FALLBACK_SIZE): ByteArray? {
        val mask = result.alphaMask ?: return null; val (background, foreground) = palette.colors(dark)
        val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.save(); canvas.scale(targetSize / IconShapePathFactory.CANONICAL_SIZE, targetSize / IconShapePathFactory.CANONICAL_SIZE)
        MaterialIconShapeRenderer.drawBackground(canvas, shape, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background })
        canvas.drawBitmap(mask, null, Rect(42, 42, 208, 208), Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = PorterDuffColorFilter(foreground, PorterDuff.Mode.SRC_IN) })
        canvas.restore()
        return if (MonetOutputValidator.validate(bitmap, foreground)) IconRenderer.bitmapToPng(bitmap) else null
    }
}
