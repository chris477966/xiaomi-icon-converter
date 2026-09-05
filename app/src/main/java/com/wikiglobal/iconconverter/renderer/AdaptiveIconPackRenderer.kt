package com.wikiglobal.iconconverter.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import com.wikiglobal.iconconverter.hyperos.IconShape
import com.wikiglobal.iconconverter.hyperos.IconShapePathFactory

/** Final production renderer shared by icon-pack Apply and icon-pack preview. */
object AdaptiveIconPackRenderer {
    const val ADAPTIVE_SAFE_SCALE = .66f
    fun renderPng(drawable: Drawable, shape: IconShape, targetSize: Int): ByteArray =
        IconRenderer.bitmapToPng(renderBitmap(drawable, shape, targetSize))

    fun renderBitmap(drawable: Drawable, shape: IconShape, targetSize: Int): Bitmap {
        require(targetSize > 0)
        val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output).apply { drawColor(Color.TRANSPARENT) }
        canvas.save(); canvas.scale(targetSize / IconShapePathFactory.CANONICAL_SIZE, targetSize / IconShapePathFactory.CANONICAL_SIZE)
        canvas.clipPath(IconShapePathFactory.path(shape))
        if (drawable is AdaptiveIconDrawable) drawAdaptive(canvas, drawable) else drawLegacy(canvas, drawable)
        canvas.restore()
        return output
    }

    private fun drawAdaptive(canvas: Canvas, icon: AdaptiveIconDrawable) {
        val safe = IconShapePathFactory.canonicalBounds
        val side = safe.width() / ADAPTIVE_SAFE_SCALE
        val rect = RectF(125f - side / 2f, 125f - side / 2f, 125f + side / 2f, 125f + side / 2f)
        drawDrawable(canvas, icon.background, rect)
        drawDrawable(canvas, icon.foreground, rect)
    }
    /** Legacy/vector icons retain their own alpha and aspect ratio: no invented backdrop or fill. */
    private fun drawLegacy(canvas: Canvas, drawable: Drawable) {
        val bounds = IconShapePathFactory.canonicalBounds
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: bounds.width().toInt()
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: bounds.height().toInt()
        val scale = minOf(bounds.width() / width, bounds.height() / height)
        val w = width * scale; val h = height * scale
        drawDrawable(canvas, drawable, RectF(125f - w / 2f, 125f - h / 2f, 125f + w / 2f, 125f + h / 2f))
    }
    private fun drawDrawable(canvas: Canvas, drawable: Drawable, target: RectF) {
        val old = Rect(drawable.bounds)
        drawable.setBounds(target.left.toInt(), target.top.toInt(), target.right.toInt(), target.bottom.toInt())
        drawable.draw(canvas); drawable.bounds = old
    }
}
