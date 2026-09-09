package com.wikiglobal.iconconverter.ui

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MaterialPreviewBitmapCacheTest {
    private fun png(color: Int): ByteArray = ByteArrayOutputStream().use { stream ->
        Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            .compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.toByteArray()
    }

    @Test fun `decode preserves PNG and pixels and reuses cached bitmap`() = runBlocking {
        val cache = MaterialPreviewBitmapCache()
        val bytes = png(Color.RED)
        val original = bytes.copyOf()
        val key = MaterialPreviewBitmapCache.Key(1, "pkg#activity", 8)
        assertEquals(0, cache.entryCount())
        val bitmap = cache.preview(key, bytes)!!
        assertEquals(Color.RED, bitmap.getPixel(4, 4))
        assertArrayEquals(original, bytes)
        assertSame(bitmap, cache.preview(key, bytes))
    }

    @Test fun `generation component and size isolate cached images`() = runBlocking {
        val cache = MaterialPreviewBitmapCache()
        val key = MaterialPreviewBitmapCache.Key(1, "pkg#first", 8)
        val red = cache.preview(key, png(Color.RED))!!
        val blue = cache.preview(key.copy(generationId=2), png(Color.BLUE))!!
        assertNotSame(red, blue)
        assertEquals(Color.BLUE, blue.getPixel(4, 4))
        assertNotSame(red, cache.preview(key.copy(componentKey="pkg#second"), png(Color.GREEN)))
        assertNull(cache.preview(key.copy(previewTargetSize=16), png(Color.RED)))
    }

    @Test fun `LRU evicts oldest without recycling a displayed bitmap`() = runBlocking {
        val cache = MaterialPreviewBitmapCache()
        val bytes = png(Color.RED)
        val first = MaterialPreviewBitmapCache.Key(1, "0", 8)
        val bitmap = cache.preview(first, bytes)!!
        for (index in 1..MaterialPreviewBitmapCache.MAX_ENTRIES) {
            cache.preview(first.copy(componentKey=index.toString()), bytes)
        }
        assertEquals(MaterialPreviewBitmapCache.MAX_ENTRIES, cache.entryCount())
        assertFalse(bitmap.isRecycled)
        assertNotSame(bitmap, cache.preview(first, bytes))
        assertEquals(MaterialPreviewBitmapCache.MAX_ENTRIES, cache.entryCount())
    }
}
