package com.wikiglobal.iconconverter.parser

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import com.wikiglobal.iconconverter.model.IconPack
import java.io.File

/** Opens an APK selected through SAF, then resolves drawables through its compiled Resources table. */
class IconPackParser(private val context: Context) {
    fun parse(uri: Uri): IconPack {
        val localApk = File(context.cacheDir, "iconpack-${System.nanoTime()}.apk")
        context.contentResolver.openInputStream(uri)?.use { input -> localApk.outputStream().use(input::copyTo) }
            ?: error("无法读取所选 APK")
        return parseApk(localApk)
    }
    /** Shared by user-selected packs and trusted bundled providers; resources are always resolved through aapt ids. */
    fun parseApk(localApk: File): IconPack {
        val packageManager = context.packageManager
        @Suppress("DEPRECATION")
        val archive = packageManager.getPackageArchiveInfo(localApk.absolutePath, 0)
            ?: error("选择的文件不是有效 APK")
        val appInfo = requireNotNull(archive.applicationInfo).apply {
            sourceDir = localApk.absolutePath; publicSourceDir = localApk.absolutePath
        }
        // PackageManager creates an AssetManager backed by the APK resource table. This deliberately avoids
        // opening ZIP entry names or assuming how aapt2 named PNG/vector files internally.
        val resources = packageManager.getResourcesForApplication(appInfo)
        val assets = resources.assets
        val loader: (String) -> Drawable? = { name ->
            val id = resources.getIdentifier(name, "drawable", archive.packageName)
            if (id == 0) null else runCatching { resources.getDrawable(id, null) }.getOrNull()
        }
        // AssetManager paths are normally relative to assets/. The second form keeps compatibility with packs
        // that package the path literally as assets/appfilter.xml.
        val appFilter = runCatching { assets.open("appfilter.xml") }.getOrElse { assets.open("assets/appfilter.xml") }
        val parsed = appFilter.use { AppFilterParser.parse(it) { name -> loader(name) != null } }
        val label = runCatching {
            if (appInfo.labelRes != 0) resources.getString(appInfo.labelRes) else appInfo.nonLocalizedLabel?.toString()
        }.getOrNull().orEmpty().ifBlank { archive.packageName }
        return IconPack(label, archive.packageName, parsed.mappings, parsed.calendars, parsed.effects, loader)
    }
}
