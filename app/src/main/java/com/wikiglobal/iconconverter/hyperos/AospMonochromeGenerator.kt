package com.wikiglobal.iconconverter.hyperos

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Apache-2.0 adaptation of AOSP's 2024 MonochromeIconFactory.
 *
 * This deliberately mirrors the upstream pixel path: flatten on black, copy through the RGB
 * average ColorMatrix into ALPHA_8, then normalise contrast according to the top/bottom edge.
 */
object AospMonochromeGenerator {
    data class Viewport(val iconSize: Int, val bitmapSize: Int, val edgePixelLength: Int, val viewPortScale: Float)

    fun viewport(iconSize: Int, extraInset: Float): Viewport {
        val viewPortScale = 1f / (1f + 2f * extraInset)
        val bitmapSize = (iconSize * 2f * viewPortScale).roundToInt()
        // Matches: mBitmapSize * (mBitmapSize - iconBitmapSize) / 2.
        val edgePixelLength = bitmapSize * (bitmapSize - iconSize) / 2
        return Viewport(iconSize, bitmapSize, edgePixelLength, viewPortScale)
    }

    /** Exact Java/AOSP byte transform, separated for byte-level reference tests. */
    fun transformPixels(source: ByteArray, edgePixelLength: Int): ByteArray? {
        if (source.isEmpty()) return null
        var min = 0xFF
        var max = 0
        source.forEach {
            val pixel = it.toInt() and 0xFF
            min = minOf(min, pixel)
            max = maxOf(max, pixel)
        }
        if (min >= max) return null
        val range = max - min
        val edgeLength = edgePixelLength.coerceIn(0, source.size / 2)
        var sum = 0
        for (i in 0 until edgeLength) {
            sum += source[i].toInt() and 0xFF
            sum += source[source.size - 1 - i].toInt() and 0xFF
        }
        val edgeAverage = if (edgeLength == 0) min.toFloat() else sum / (edgeLength * 2f)
        val flipColor = (edgeAverage - min) / range > .5f
        val output = ByteArray(source.size)
        source.indices.forEach { index ->
            val pixel = source[index].toInt() and 0xFF
            val mapped = ((pixel - min) * 0xFF / range.toFloat()).roundToInt().coerceIn(0, 255)
            output[index] = (if (flipColor) 255 - mapped else mapped).toByte()
        }
        output.indices.forEach { index -> output[index] = secondContrast(output[index].toInt() and 0xFF).toByte() }
        return output
    }

    /** Compatibility bridge retained for existing pure tests. */
    fun transform(gray: IntArray, edgePixelLength: Int): IntArray? =
        transformPixels(ByteArray(gray.size) { gray[it].coerceIn(0, 255).toByte() }, edgePixelLength)
            ?.let { result -> IntArray(result.size) { result[it].toInt() and 0xFF } }

    /** AOSP uses truncation, not rounding, in the second contrast phase. */
    fun secondContrast(pixel: Int): Int {
        val p = pixel.coerceIn(0, 255)
        val result = if (p > 128) {
            val coefficient = 1 - (p - 128).toDouble() / 128
            255 - (coefficient * (255 - p)).toInt()
        } else {
            val coefficient = 1 - (128 - p).toDouble() / 128
            (coefficient * p).toInt()
        }
        return result.coerceIn(0, 255)
    }

    fun generate(icon: AdaptiveIconDrawable): MonetGlyphResult = runCatching {
        val viewport = viewport(HyperOs3ThemePatcher.ICON_SIZE, AdaptiveIconDrawable.getExtraInsetFraction())
        val flat = Bitmap.createBitmap(viewport.bitmapSize, viewport.bitmapSize, Bitmap.Config.ARGB_8888)
        val flatCanvas = Canvas(flat)
        flatCanvas.drawColor(Color.BLACK)

        // Exactly AOSP drawDrawable: AdaptiveIconDrawable manages its own inset semantics.
        drawFullBitmapBounds(icon.background, flatCanvas, viewport.bitmapSize)
        drawFullBitmapBounds(icon.foreground, flatCanvas, viewport.bitmapSize)

        val alpha = Bitmap.createBitmap(viewport.bitmapSize, viewport.bitmapSize, Bitmap.Config.ALPHA_8)
        val copyPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) blendMode = BlendMode.SRC
            else xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
            colorFilter = ColorMatrixColorFilter(ColorMatrix().also { matrix ->
                matrix.array[15] = .3333f
                matrix.array[16] = .3333f
                matrix.array[17] = .3333f
                matrix.array[18] = 0f
                matrix.array[19] = 0f
            })
        }
        Canvas(alpha).drawBitmap(flat, 0f, 0f, copyPaint)

        val raw = ByteArray(viewport.bitmapSize * viewport.bitmapSize)
        alpha.copyPixelsToBuffer(ByteBuffer.wrap(raw))
        val transformed = transformPixels(raw, viewport.edgePixelLength)
            ?: return@runCatching MonetGlyphResult(null, MonetGlyphSource.UNAVAILABLE_EMPTY, null, MonetGlyphSource.UNAVAILABLE_EMPTY)
        alpha.copyPixelsFromBuffer(ByteBuffer.wrap(transformed))
        MonetGlyphResult(
            alpha,
            MonetGlyphSource.AOSP_FORCED_MONOCHROME,
            GlyphSafetyAnalyzer.analyze(IntArray(transformed.size) { transformed[it].toInt() and 0xFF }, viewport.bitmapSize, viewport.bitmapSize)
        )
    }.getOrElse { MonetGlyphResult(null, MonetGlyphSource.UNAVAILABLE_EMPTY, null, MonetGlyphSource.UNAVAILABLE_EMPTY) }

    private fun drawFullBitmapBounds(drawable: Drawable?, canvas: Canvas, bitmapSize: Int) {
        drawable ?: return
        val oldBounds = Rect(drawable.bounds)
        val target = fullBitmapBounds(bitmapSize)
        drawable.setBounds(target.left, target.top, target.right, target.bottom)
        drawable.draw(canvas)
        drawable.bounds = oldBounds
    }

    /** Exposed for the exact-bounds unit test; both layers use this AOSP rectangle. */
    data class LayerBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)
    fun fullBitmapBounds(bitmapSize: Int) = LayerBounds(0, 0, bitmapSize, bitmapSize)
}
