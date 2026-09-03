package com.wikiglobal.iconconverter.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import java.io.ByteArrayOutputStream

/** Renders any framework Drawable (including vector/adaptive/bitmap drawables) into a transparent PNG. */
object IconRenderer {
    const val TARGET_SIZE = 256
    fun renderPng(drawable: Drawable, size: Int = TARGET_SIZE): ByteArray {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(Color.TRANSPARENT) }
        val original = drawable.bounds
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        drawable.bounds = original
        return bitmapToPng(bitmap)
    }
    fun bitmapToPng(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() }
}
