package com.wikiglobal.iconconverter.hyperos

import android.content.res.Resources
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.PathParser

/** The one shared shape vocabulary for icon-pack and Material You rendering. */
enum class IconShape { SYSTEM, HYPEROS, CIRCLE, SQUIRCLE, ROUNDED_SQUARE, SQUARE, TEARDROP }

/**
 * Geometry is expressed in the verified 250-coordinate space. Renderers scale
 * their canvas, rather than independently reinterpreting a shape at 288px.
 */
object IconShapePathFactory {
    const val CANONICAL_SIZE = 250f
    const val LAWNCHAIR_SQUIRCLE_CONTROL_DISTANCE = .2f
    const val LAWNCHAIR_ROUNDED_SQUARE_SCALE = .6f
    val canonicalBounds = RectF(12f, 12f, 238f, 238f)

    fun path(shape: IconShape, bounds: RectF = canonicalBounds, resources: Resources? = null): Path = when (shape) {
        IconShape.SYSTEM -> systemPath(resources, bounds) ?: path(IconShape.HYPEROS, bounds)
        IconShape.HYPEROS -> Path().apply { addRoundRect(bounds, 58f, 58f, Path.Direction.CW) }
        IconShape.CIRCLE -> Path().apply { addOval(bounds, Path.Direction.CW) }
        IconShape.SQUIRCLE -> squircle(bounds)
        IconShape.ROUNDED_SQUARE -> Path().apply {
            val radius = minOf(bounds.width(), bounds.height()) / 2f * LAWNCHAIR_ROUNDED_SQUARE_SCALE
            addRoundRect(bounds, radius, radius, Path.Direction.CW)
        }
        IconShape.SQUARE -> Path().apply { addRect(bounds, Path.Direction.CW) }
        IconShape.TEARDROP -> teardrop(bounds)
    }

    fun systemPath(resources: Resources? = Resources.getSystem(), bounds: RectF = canonicalBounds): Path? = runCatching {
        val res = resources ?: return null
        val id = res.getIdentifier("config_icon_mask", "string", "android")
        if (id == 0) return null
        val raw = PathParser.createPathFromPathData(res.getString(id)) ?: return null
        val source = RectF().also { out -> raw.computeBounds(out, true) }
        if (source.width() <= 0f || source.height() <= 0f) return null
        raw.transform(android.graphics.Matrix().apply {
            setScale(bounds.width() / source.width(), bounds.height() / source.height())
            postTranslate(bounds.left - source.left * bounds.width() / source.width(), bounds.top - source.top * bounds.height() / source.height())
        })
        raw
    }.getOrNull()

    fun squircle(rect: RectF): Path {
        val size = minOf(rect.width(), rect.height()) / 2f
        val offset = size * LAWNCHAIR_SQUIRCLE_CONTROL_DISTANCE
        return Path().apply {
            moveTo(rect.right - size, rect.top)
            cubicTo(rect.right - offset, rect.top, rect.right, rect.top + offset, rect.right, rect.top + size)
            cubicTo(rect.right, rect.bottom - offset, rect.right - offset, rect.bottom, rect.right - size, rect.bottom)
            cubicTo(rect.left + offset, rect.bottom, rect.left, rect.bottom - offset, rect.left, rect.bottom - size)
            cubicTo(rect.left, rect.top + offset, rect.left + offset, rect.top, rect.left + size, rect.top)
            close()
        }
    }

    /** AdaptiveIconBitmap-compatible teardrop path, scaled from the documented 100x100 path. */
    fun teardrop(bounds: RectF): Path {
        val sx = bounds.width() / 100f; val sy = bounds.height() / 100f
        return Path().apply {
            moveTo(bounds.left + 50f * sx, bounds.top)
            arcTo(RectF(bounds.left, bounds.top, bounds.left + 100f * sx, bounds.top + 100f * sy), -90f, 180f, false)
            lineTo(bounds.right, bounds.top + 85f * sy)
            arcTo(RectF(bounds.right - 30f * sx, bounds.bottom - 30f * sy, bounds.right, bounds.bottom), 0f, 90f, false)
            lineTo(bounds.left + 50f * sx, bounds.bottom)
            arcTo(RectF(bounds.left, bounds.top, bounds.left + 100f * sx, bounds.top + 100f * sy), 90f, 180f, false)
            close()
        }
    }
}
