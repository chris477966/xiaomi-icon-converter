package com.wikiglobal.iconconverter.hyperos

import java.io.File
import java.util.zip.ZipFile

data class HyperOsThemeProfile(
    val staticIconSize: Int,
    val drawablePrefix: String,
    val detectedIconCount: Int,
    val confidence: Float
)

/** Reads only PNG IHDR headers. It never decodes a full icon archive. */
object HyperOsThemeProfileDetector {
    const val FALLBACK_SIZE = 250
    fun detect(archive: File): HyperOsThemeProfile = runCatching {
        val sizes = mutableMapOf<Int, Int>()
        ZipFile(archive).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.isDirectory && entry.name.startsWith(HyperOs3ThemePatcher.DRAWABLE_PREFIX) && entry.name.endsWith(".png")) {
                    zip.getInputStream(entry).use { input ->
                        val header = ByteArray(33)
                        val count = input.read(header)
                        pngSize(if (count > 0) header.copyOf(count) else ByteArray(0))
                    }
                        ?.takeIf { (w, h) -> w == h && w in 64..1024 }
                        ?.let { (w, _) -> sizes[w] = (sizes[w] ?: 0) + 1 }
                }
            }
        }
        val dominant = sizes.maxByOrNull { it.value }
        if (dominant == null) HyperOsThemeProfile(FALLBACK_SIZE, HyperOs3ThemePatcher.DRAWABLE_PREFIX, 0, 0f)
        else HyperOsThemeProfile(dominant.key, HyperOs3ThemePatcher.DRAWABLE_PREFIX, sizes.values.sum(), dominant.value.toFloat() / sizes.values.sum())
    }.getOrDefault(HyperOsThemeProfile(FALLBACK_SIZE, HyperOs3ThemePatcher.DRAWABLE_PREFIX, 0, 0f))

    private fun pngSize(header: ByteArray): Pair<Int, Int>? {
        val signature = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        if (header.size < 24 || !header.copyOfRange(0, 8).contentEquals(signature)) return null
        fun value(i: Int) = ((header[i].toInt() and 255) shl 24) or ((header[i + 1].toInt() and 255) shl 16) or ((header[i + 2].toInt() and 255) shl 8) or (header[i + 3].toInt() and 255)
        return value(16) to value(20)
    }
}
