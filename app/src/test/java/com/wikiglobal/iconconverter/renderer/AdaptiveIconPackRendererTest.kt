package com.wikiglobal.iconconverter.renderer

import android.content.res.Resources
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.wikiglobal.iconconverter.hyperos.HyperOs3ThemePatcher
import com.wikiglobal.iconconverter.hyperos.IconShape
import com.wikiglobal.iconconverter.hyperos.IconShapePathFactory
import com.wikiglobal.iconconverter.hyperos.PngValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdaptiveIconPackRendererTest {
    @Test fun `SYSTEM_USES_ANDROID_CONFIG_ICON_MASK`() {
        val system = IconShapePathFactory.path(IconShape.SYSTEM, resources = resources("M0,0 L250,0 L250,250 L0,250 Z"))
        val hyperOs = IconShapePathFactory.path(IconShape.HYPEROS)
        assertFalse(pathSignature(system).contentEquals(pathSignature(hyperOs)))
    }

    @Test fun `SYSTEM_INVALID_MASK_FALLBACK_HYPEROS`() {
        val fallback = IconShapePathFactory.path(IconShape.SYSTEM, resources = resources("not valid path"))
        assertTrue(pathSignature(fallback).contentEquals(pathSignature(IconShapePathFactory.path(IconShape.HYPEROS))))
    }

    @Test fun `TEARDROP_PATH_GOLDEN`() {
        val points = IconShapePathFactory.path(IconShape.TEARDROP, android.graphics.RectF(0f, 0f, 100f, 100f)).approximate(.05f)
        assertTrue(hasPoint(points, 50f, 0f)); assertTrue(hasPoint(points, 100f, 50f)); assertTrue(hasPoint(points, 100f, 85f))
        assertTrue(hasPoint(points, 85f, 100f)); assertTrue(hasPoint(points, 50f, 100f))
    }

    @Test fun `ADAPTIVE_FOREGROUND_PRESERVED_BACKGROUND_PRESERVED_AND_OUTSIDE_MASK_ALPHA_ZERO`() {
        val adaptive = icon()
        assertTrue(adaptive.foreground is BitmapDrawable)
        val foregroundOnly = Bitmap.createBitmap(250, 250, Bitmap.Config.ARGB_8888)
        adaptive.foreground.setBounds(-46, -46, 296, 296)
        adaptive.foreground.draw(Canvas(foregroundOnly))
        assertEquals(Color.BLUE, foregroundOnly.getPixel(125, 125))
        val bitmap = AdaptiveIconPackRenderer.renderBitmap(adaptive, IconShape.CIRCLE, 250)
        assertEquals(Color.BLUE, bitmap.getPixel(125, 125))
        assertEquals(Color.RED, bitmap.getPixel(125, 45))
        assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
    }

    @Test fun `ADAPTIVE_SHAPE_BITMAP_HASHES_DIFFER_AT_250_AND_288`() {
        listOf(250, 288).forEach { size ->
            val circle = AdaptiveIconPackRenderer.renderBitmap(icon(), IconShape.CIRCLE, size)
            val square = AdaptiveIconPackRenderer.renderBitmap(icon(), IconShape.SQUARE, size)
            val squircle = AdaptiveIconPackRenderer.renderBitmap(icon(), IconShape.SQUIRCLE, size)
            val teardrop = AdaptiveIconPackRenderer.renderBitmap(icon(), IconShape.TEARDROP, size)
            val hyperOs = AdaptiveIconPackRenderer.renderBitmap(icon(), IconShape.HYPEROS, size)
            val system = AdaptiveIconPackRenderer.renderBitmap(icon(), IconShape.SYSTEM, size, resources("M0,0 L250,0 L250,250 L0,250 Z"))
            assertEquals(size, circle.width); assertEquals(size, circle.height)
            assertFalse("circle vs square at $size", hash(circle) == hash(square))
            assertFalse("squircle vs teardrop at $size", hash(squircle) == hash(teardrop))
            assertFalse("hyperos vs system fixture at $size", hash(hyperOs) == hash(system))
            assertTrue(PngValidator.validate(AdaptiveIconPackRenderer.renderPng(icon(), IconShape.CIRCLE, size), size).isValidIcon)
        }
    }

    private fun icon(): AdaptiveIconDrawable {
        val foreground = Bitmap.createBitmap(342, 342, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).drawRect(150f, 150f, 192f, 192f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLUE })
        }
        return AdaptiveIconDrawable(ColorDrawable(Color.RED), BitmapDrawable(Resources.getSystem(), foreground))
    }
    private fun resources(mask: String): Resources {
        val resources = Mockito.mock(Resources::class.java)
        Mockito.`when`(resources.getIdentifier("config_icon_mask", "string", "android")).thenReturn(1)
        Mockito.`when`(resources.getString(1)).thenReturn(mask)
        return resources
    }
    private fun pathSignature(path: android.graphics.Path) = path.approximate(.1f).joinToString(",") { it.toRawBits().toString() }.toByteArray()
    private fun hasPoint(values: FloatArray, x: Float, y: Float) = values.asList().chunked(3).any { it.size == 3 && kotlin.math.abs(it[1] - x) < .6f && kotlin.math.abs(it[2] - y) < .6f }
    private fun hash(bitmap: android.graphics.Bitmap): String { val pixels = IntArray(bitmap.width * bitmap.height); bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height); return MessageDigest.getInstance("SHA-256").digest(pixels.joinToString().toByteArray()).joinToString("") { "%02x".format(it) } }

}
