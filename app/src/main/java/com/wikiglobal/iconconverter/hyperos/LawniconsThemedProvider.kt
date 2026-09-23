package com.wikiglobal.iconconverter.hyperos

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.security.MessageDigest

enum class LawniconsProviderStatus {
    UNINITIALIZED, READY, APK_SHA_FAILED, APK_RESOURCES_FAILED,
    GRAYSCALE_MAP_NOT_FOUND, GRAYSCALE_MAP_PARSE_FAILED, EMPTY_MAP
}

data class LawniconsThemeEntry(val packageName: String, val drawableResourceId: Int, val drawableName: String? = null)
data class LawniconsProviderState(val status: LawniconsProviderStatus = LawniconsProviderStatus.UNINITIALIZED, val entryCount: Int = 0, val lastError: String? = null)
/** Package-only index, matching Lawnchair's grayscale_icon_map lookup contract. */
class LawniconsThemedIndex(entries: Iterable<LawniconsThemeEntry>) {
    private val byPackage = entries.associateBy { it.packageName }
    fun lookup(packageName: String) = byPackage[packageName]
    val size get() = byPackage.size
}

/**
 * Lawnchair-compatible themed-icon provider. It consumes Lawnicons' generated
 * xml/grayscale_icon_map rather than the ordinary appfilter.xml icon-pack mapping.
 */
class LawniconsThemedProvider(private val context: Context) {
    companion object {
        const val BUNDLED_APK_SHA256 = "e830b37e1cd7cd66487492f4a1084ba086254b2b09541d729da0ec2169a73bfe"
    }

    private var resources: Resources? = null
    private var index = LawniconsThemedIndex(emptyList())
    var state: LawniconsProviderState = LawniconsProviderState(); private set

    fun load(): LawniconsProviderState {
        if (state.status == LawniconsProviderStatus.READY) return state
        val target = File(context.cacheDir, "provider/lawnicons-2.18.0.apk")
        try {
            target.parentFile?.mkdirs()
            if (!target.exists() || sha256(target) != BUNDLED_APK_SHA256) {
                context.assets.open("providers/Lawnicons.2.18.0.apk").use { input -> target.outputStream().use(input::copyTo) }
            }
            if (sha256(target) != BUNDLED_APK_SHA256) return fail(LawniconsProviderStatus.APK_SHA_FAILED, "Lawnicons APK SHA-256 verification failed")
            val appInfo = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageArchiveInfo(target.path, PackageManager.PackageInfoFlags.of(0))?.applicationInfo
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(target.path, 0)?.applicationInfo
            }) ?: return fail(LawniconsProviderStatus.APK_RESOURCES_FAILED, "PackageArchiveInfo unavailable")
            appInfo.sourceDir = target.path
            appInfo.publicSourceDir = target.path
            val archiveResources = runCatching { context.packageManager.getResourcesForApplication(appInfo) }.getOrElse {
                return fail(LawniconsProviderStatus.APK_RESOURCES_FAILED, it.message ?: "Lawnicons resources unavailable")
            }
            val xmlId = archiveResources.getIdentifier("grayscale_icon_map", "xml", appInfo.packageName)
            if (xmlId == 0) return fail(LawniconsProviderStatus.GRAYSCALE_MAP_NOT_FOUND, "xml/grayscale_icon_map not found")
            val parsed = runCatching { parseMap(archiveResources, xmlId) }.getOrElse {
                return fail(LawniconsProviderStatus.GRAYSCALE_MAP_PARSE_FAILED, it.message ?: "grayscale_icon_map parse failed")
            }
            if (parsed.isEmpty()) return fail(LawniconsProviderStatus.EMPTY_MAP, "grayscale_icon_map contains no usable entries")
            resources = archiveResources
            index = LawniconsThemedIndex(parsed.values)
            state = LawniconsProviderState(LawniconsProviderStatus.READY, index.size, null)
            return state
        } catch (error: Throwable) {
            return fail(LawniconsProviderStatus.APK_RESOURCES_FAILED, error.message ?: error.javaClass.simpleName)
        }
    }

    fun lookup(packageName: String): LawniconsThemeEntry? = index.lookup(packageName)
    fun drawable(entry: LawniconsThemeEntry): Drawable? = resources?.let { runCatching { it.getDrawable(entry.drawableResourceId, null) }.getOrNull() }
    fun alphaMask(drawable: Drawable): Bitmap? {
        val size = 256; val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val oldBounds = Rect(drawable.bounds); drawable.setBounds(0, 0, size, size); drawable.draw(Canvas(bitmap)); drawable.bounds = oldBounds
        val pixels = IntArray(size * size); bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        pixels.indices.forEach { pixels[it] = (pixels[it] ushr 24) shl 24 }
        val visible = pixels.count { (it ushr 24) > 0 }
        return if (visible in 16 until pixels.size) bitmap.also { it.setPixels(pixels, 0, size, 0, 0, size, size) } else null
    }

    private fun parseMap(resources: Resources, xmlId: Int): Map<String, LawniconsThemeEntry> {
        val parsed = linkedMapOf<String, LawniconsThemeEntry>()
        val parser = resources.getXml(xmlId)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG || parser.name != "icon") continue
            val packageName = parser.getAttributeValue(null, "package")?.trim().orEmpty()
            val drawableId = parser.getAttributeResourceValue(null, "drawable", 0)
            if (packageName.isNotEmpty() && drawableId != 0) {
                parsed.putIfAbsent(packageName, LawniconsThemeEntry(packageName, drawableId, runCatching { resources.getResourceEntryName(drawableId) }.getOrNull()))
            }
        }
        return parsed
    }

    private fun fail(status: LawniconsProviderStatus, error: String): LawniconsProviderState {
        resources = null; index = LawniconsThemedIndex(emptyList()); state = LawniconsProviderState(status, 0, error); return state
    }
    private fun sha256(file: File) = file.inputStream().use { input -> MessageDigest.getInstance("SHA-256").digest(input.readBytes()).joinToString("") { "%02x".format(it) } }
}
