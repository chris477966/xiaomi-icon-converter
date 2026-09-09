package com.wikiglobal.iconconverter.ui

import android.graphics.Bitmap
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Screen-owned, bounded cache. Canonical PNGs remain owned by MonetUiState. */
class MaterialPreviewBitmapCache {
    data class Key(val generationId: Long, val componentKey: String, val previewTargetSize: Int)

    private val bitmaps = LruCache<Key, Bitmap>(MAX_ENTRIES)

    suspend fun preview(key: Key, png: ByteArray): Bitmap? = withContext(Dispatchers.Default) {
        bitmaps.get(key) ?: PreviewBitmapPipeline.decodeMaterialPng(png, key.previewTargetSize)?.also {
            // Never recycle evicted bitmaps: a composed tile may still display one.
            bitmaps.put(key, it)
        }
    }

    internal fun entryCount(): Int = bitmaps.size()

    companion object {
        const val MAX_ENTRIES = 36
    }
}
