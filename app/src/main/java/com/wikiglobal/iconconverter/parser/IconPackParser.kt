package com.wikiglobal.iconconverter.parser

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import com.wikiglobal.iconconverter.model.IconPack
import com.wikiglobal.iconconverter.model.IconEntry
import java.io.File
import java.util.zip.ZipFile

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
            val idAndType = listOf("drawable", "mipmap")
                .asSequence()
                .map { it to resources.getIdentifier(name, it, archive.packageName) }
                .firstOrNull { it.second != 0 }
            idAndType?.second?.let { runCatching { resources.getDrawable(it, null) }.getOrNull() }
        }
        // AssetManager paths are normally relative to assets/. The second form keeps compatibility with packs
        // that package the path literally as assets/appfilter.xml.
        val parsed = runCatching {
            val appFilter = runCatching { assets.open("appfilter.xml") }
                .getOrElse { assets.open("assets/appfilter.xml") }
            appFilter.use { AppFilterParser.parse(it) { name -> loader(name) != null } }
        }.getOrDefault(com.wikiglobal.iconconverter.parser.ParsedAppFilter(emptyList(), emptyList(), com.wikiglobal.iconconverter.model.IconEffects()))
        val label = runCatching {
            if (appInfo.labelRes != 0) resources.getString(appInfo.labelRes) else appInfo.nonLocalizedLabel?.toString()
        }.getOrNull().orEmpty().ifBlank { archive.packageName }
        val mappedPackages = parsed.mappings.groupBy { it.drawableName }
            .mapValues { (_, mappings) -> mappings.map { it.component.packageName }.toSet() }
        val entries = iconResourceCandidates(localApk, archive.packageName, resources)
            .map { candidate ->
                IconEntry(
                    iconPackId = archive.packageName,
                    resourceName = candidate.name,
                    resourceIdentifier = candidate.id,
                    resourceType = candidate.type,
                    mappedPackageNames = mappedPackages[candidate.name].orEmpty(),
                    searchableKeywords = setOf(candidate.name) + mappedPackages[candidate.name].orEmpty()
                )
            }
            .distinctBy { it.resourceType to it.resourceName }
            .sortedBy { it.resourceName }
        val versionName = archive.versionName?.toString()?.takeIf { it.isNotBlank() }
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) archive.longVersionCode else archive.versionCode.toLong()
        return IconPack(label, archive.packageName, parsed.mappings, parsed.calendars, parsed.effects, loader, archive.packageName, versionName, versionCode, entries) {
            runCatching { resources.getDrawable(it, null) }.getOrNull()
        }
    }

    private data class ResourceCandidate(val name: String, val type: String, val id: Int)

    /**
     * APK entries are only candidate names. Every candidate is validated through the compiled
     * Resources table before it becomes an IconEntry; no ZIP path is used as a drawable lookup.
     */
    private fun iconResourceCandidates(apk: File, packageName: String, resources: android.content.res.Resources): List<ResourceCandidate> {
        val candidates = linkedSetOf<Pair<String, String>>()
        runCatching {
            ZipFile(apk).use { zip ->
                zip.entries().asSequence().map { it.name }.forEach { path ->
                    val parts = path.split('/')
                    if (parts.size != 3 || parts[0] != "res") return@forEach
                    val type = parts[1].substringBefore('-')
                    if (type != "drawable" && type != "mipmap") return@forEach
                    val raw = parts[2].substringBeforeLast('.')
                    val name = raw.removeSuffix(".9")
                    if (name.isNotBlank()) candidates += type to name
                }
            }
        }
        return candidates.mapNotNull { (type, name) ->
            val id = resources.getIdentifier(name, type, packageName)
            id.takeIf { it != 0 }?.let { ResourceCandidate(name, type, it) }
        }
    }
}
