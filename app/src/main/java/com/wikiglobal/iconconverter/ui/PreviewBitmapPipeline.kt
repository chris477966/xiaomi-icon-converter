package com.wikiglobal.iconconverter.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.wikiglobal.iconconverter.renderer.IconRenderer

/**
 * Current-generation bitmap cache helpers. All expensive PNG decoding and
 * Drawable rasterization happens before a Lazy item is composed.
 */
object PreviewBitmapPipeline {
    const val MATERIAL_PNG_SIZE = 250
    const val MATERIAL_PREVIEW_DP = 80
    const val ICON_PACK_PREVIEW_SIZE = 128

    fun decodeMaterialPng(png: ByteArray): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        return BitmapFactory.decodeByteArray(png, 0, png.size, options)
            ?.takeIf { it.width == MATERIAL_PNG_SIZE && it.height == MATERIAL_PNG_SIZE }
    }

    fun decodeMaterialPngs(pngs: Map<String, ByteArray>): Map<String, Bitmap> =
        pngs.mapNotNull { (key, png) -> decodeMaterialPng(png)?.let { key to it } }.toMap()

    fun rasterizeIcon(drawable: android.graphics.drawable.Drawable): Bitmap =
        IconRenderer.renderBitmap(drawable, ICON_PACK_PREVIEW_SIZE)
}

data class IconPackPreviewBitmaps(
    val original: Map<String, Bitmap> = emptyMap(),
    val target: Map<String, Bitmap> = emptyMap()
)

fun componentPreviewKey(packageName: String, launcherActivity: String) = "$packageName#$launcherActivity"
