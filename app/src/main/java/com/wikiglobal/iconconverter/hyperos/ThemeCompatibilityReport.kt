package com.wikiglobal.iconconverter.hyperos

data class RootFileMetadata(
    val path: String,
    val fileType: String,
    val sizeBytes: Long?,
    val sha256: String?,
    val uid: String?,
    val gid: String?,
    val mode: String?,
    val selinuxContext: String?
)

data class ArchiveFlags(
    val hasTransformConfig: Boolean = false,
    val hasDrawableXxhdpi: Boolean = false,
    val hasPackageDirectories: Boolean = false,
    val hasPackagePng: Boolean = false,
    val hasLayer0Png: Boolean = false,
    val hasLayer1Png: Boolean = false,
    val hasFancyIcons: Boolean = false,
    val hasFancyManifest: Boolean = false,
    val hasDynamicIcons: Boolean = false,
    val hasLayerAnimatingIcons: Boolean = false
)

data class ThemeArchiveReport(
    val path: String,
    val isZipCompatible: Boolean,
    val entries: List<String> = emptyList(),
    val topLevelPaths: List<String> = emptyList(),
    val flags: ArchiveFlags = ArchiveFlags(),
    val transformConfigSummary: String = "NOT_FOUND",
    val packageDirectoryTrees: Map<String, List<String>> = emptyMap(),
    val fancyIconTrees: Map<String, List<String>> = emptyMap(),
    val fancyManifestSummaries: Map<String, String> = emptyMap(),
    val dynamicTopLevel: List<String> = emptyList(),
    val packageSamples: List<IconPackageSample> = emptyList()
)

data class PngMetadata(val entry: String, val width: Int?, val height: Int?, val colorType: String, val alpha: String, val sizeBytes: Int?)
data class IconPackageSample(val packageName: String, val structure: String, val entryTree: List<String>, val pngs: List<PngMetadata>)
data class AdaptiveIconSample(
    val packageName: String,
    val activityName: String,
    val activityIconId: Int,
    val applicationIconId: Int,
    val getActivityIconClass: String,
    val loadIconClass: String,
    val rawIconClass: String,
    val category: String,
    val foregroundClass: String? = null,
    val backgroundClass: String? = null,
    val monochrome: String = "NOT_APPLICABLE"
)
data class AdaptiveIconReport(
    val launcherActivities: Int,
    val adaptiveNative: Int,
    val nativeMonochrome: Int,
    val noMonochrome: Int,
    val bitmapLegacy: Int,
    val vector: Int,
    val other: Int,
    val samples: List<AdaptiveIconSample>
)

data class LauncherInfo(val versionName: String, val versionCode: Long, val apkPath: String)

data class ThemeCompatibilityReport(
    val rootAvailable: Boolean,
    val systemProperties: Map<String, String>,
    val paths: List<RootFileMetadata>,
    val archives: List<ThemeArchiveReport>,
    val launcher: LauncherInfo?,
    val monetColors: Map<String, String>,
    val adaptiveIcons: AdaptiveIconReport,
    val notes: List<String> = emptyList()
) {
    fun toText(): String = buildString {
        appendLine("HyperOS 3 Theme Compatibility Probe")
        appendLine("ROOT_READ_ONLY=$rootAvailable")
        appendLine("\n[SYSTEM_PROPERTIES]")
        systemProperties.forEach { (key, value) -> appendLine("$key=$value") }
        appendLine("\n[THEME_PATHS]")
        paths.forEach { item -> appendLine("${item.path}\ttype=${item.fileType}\tsize=${item.sizeBytes ?: "UNKNOWN"}\tsha256=${item.sha256 ?: "UNKNOWN"}\tuid=${item.uid ?: "UNKNOWN"}\tgid=${item.gid ?: "UNKNOWN"}\tmode=${item.mode ?: "UNKNOWN"}\tselinux=${item.selinuxContext ?: "UNKNOWN"}") }
        archives.forEach { archive ->
            val dynamic = archive.path.endsWith("/dynamicicons")
            appendLine(if (dynamic) "\n[DEFAULT_DYNAMICICONS_ARCHIVE]" else "\n[ACTIVE_ICONS_ARCHIVE]")
            appendLine("${archive.path}\tZIP_COMPATIBLE=${archive.isZipCompatible}")
            appendLine("ENTRY_COUNT=${archive.entries.size}")
            appendLine("TOP_LEVEL_PATHS=${archive.topLevelPaths.joinToString()}")
            appendLine("FLAGS=${archive.flags}")
            appendLine("TRANSFORM_CONFIG=${archive.transformConfigSummary}")
            appendLine("ENTRIES (${archive.entries.size}):")
            archive.entries.forEach(::appendLine)
            appendLine(if (dynamic) "DYNAMIC_SAMPLE_TREE" else "ACTIVE_ICONS_SAMPLE_TREE")
            archive.packageDirectoryTrees.forEach { (dir, tree) -> appendLine("PACKAGE_TREE $dir: ${tree.joinToString()}") }
            archive.fancyIconTrees.forEach { (dir, tree) -> appendLine("FANCY_TREE $dir: ${tree.joinToString()}") }
            archive.fancyManifestSummaries.forEach { (path, summary) -> appendLine("[FANCY_MANIFEST] $path: $summary") }
            if (archive.dynamicTopLevel.isNotEmpty()) appendLine("DYNAMIC_TOP_LEVEL=${archive.dynamicTopLevel.joinToString()}")
            archive.packageSamples.forEach { sample ->
                appendLine("PNG_SAMPLE package=${sample.packageName} structure=${sample.structure}")
                appendLine("TREE=${sample.entryTree.joinToString()}")
                sample.pngs.forEach { png -> appendLine("PNG ${png.entry} ${png.width}x${png.height} colorType=${png.colorType} alpha=${png.alpha} size=${png.sizeBytes}") }
            }
        }
        appendLine("\n[LAUNCHER]\n${launcher ?: "NOT_FOUND"}")
        appendLine("\n[MONET]")
        monetColors.forEach { (key, value) -> appendLine("$key=$value") }
        appendLine("\n[ADAPTIVE_ICON_PROBE]")
        appendLine("Launcher Activities=${adaptiveIcons.launcherActivities}\nAdaptive icons=${adaptiveIcons.adaptiveNative}\nNative monochrome=${adaptiveIcons.nativeMonochrome}\nNo monochrome=${adaptiveIcons.noMonochrome}\nBitmap/legacy=${adaptiveIcons.bitmapLegacy}\nVector=${adaptiveIcons.vector}\nOther=${adaptiveIcons.other}")
        adaptiveIcons.samples.forEach { sample -> appendLine("${sample.packageName}/${sample.activityName} activityInfo.icon=${sample.activityIconId} applicationInfo.icon=${sample.applicationIconId} getActivityIcon=${sample.getActivityIconClass} loadIcon=${sample.loadIconClass} raw=${sample.rawIconClass} category=${sample.category} foreground=${sample.foregroundClass ?: "-"} background=${sample.backgroundClass ?: "-"} monochrome=${sample.monochrome}") }
        appendLine("\n[CONCLUSION]")
        val static = archives.firstOrNull { it.path.endsWith("/icons") }?.let { archive -> when {
            archive.flags.hasLayer0Png || archive.flags.hasLayer1Png -> "LAYERED_0_1"
            archive.flags.hasPackagePng -> "PACKAGE_PNG"
            archive.entries.any { it.endsWith(".png", true) } -> "OTHER"
            else -> "UNKNOWN"
        } } ?: "UNKNOWN"
        val dynamic = archives.firstOrNull { it.path.endsWith("/dynamicicons") }?.let { archive -> when {
            archive.flags.hasFancyIcons -> "FANCY_ICONS"
            archive.flags.hasLayerAnimatingIcons -> "LAYER_ANIMATING_ICONS"
            archive.entries.isNotEmpty() -> "OTHER"
            else -> "UNKNOWN"
        } } ?: "UNKNOWN"
        appendLine("STATIC=$static\nTRANSFORM_CONFIG=${archives.any { it.flags.hasTransformConfig }}\nDYNAMIC=$dynamic")
        if (notes.isNotEmpty()) appendLine("\n[NOTES]\n${notes.joinToString("\n")}")
    }
}
