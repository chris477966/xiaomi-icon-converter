package com.wikiglobal.iconconverter.hyperos

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MaterialPreviewApplyIdentityTest {
    @Test fun `MATERIAL_PREVIEW_APPLY_IDENTITY_250`() = assertIdentity(250)
    @Test fun `MATERIAL_PREVIEW_APPLY_IDENTITY_288`() = assertIdentity(288)

    private fun assertIdentity(targetSize: Int) {
        val mask = Bitmap.createBitmap(250, 250, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).drawCircle(125f, 125f, 52f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        }
        val glyph = MonetGlyphResult(mask, MonetGlyphSource.MANUAL_KEEP, null)
        val palette = MonetPalette(0xffc9d8ff.toInt(), 0xffafc6ff.toInt(), Color.WHITE, 0xff002f65.toInt(), null, null)
        val previewCanonicalBytes = requireNotNull(MaterialStyledGlyphRenderer.render(glyph, palette, false, IconShape.HYPEROS, targetSize))
        // Apply consumes the same `monet.generated` canonical byte array rather than re-rendering it.
        val applyCanonicalBytes = previewCanonicalBytes
        assertTrue(previewCanonicalBytes.contentEquals(applyCanonicalBytes))
        assertTrue(PngValidator.validate(applyCanonicalBytes, targetSize).isValidIcon)
        assertEquals(targetSize, android.graphics.BitmapFactory.decodeByteArray(previewCanonicalBytes, 0, previewCanonicalBytes.size).width)
    }
}
