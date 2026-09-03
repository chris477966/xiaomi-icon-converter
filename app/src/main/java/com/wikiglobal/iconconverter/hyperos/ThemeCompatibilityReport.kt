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
    val flags: ArchiveFlags = ArchiveFlags(),
    val transformConfigSummary: String = "NOT_FOUND",
    val packageDirectoryTrees: Map<String, List<String>> = emptyMap(),
    val fancyIconTrees: Map<String, List<String>> = emptyMap(),
    val fancyManifestSummaries: Map<String, String> = emptyMap(),
    val dynamicTopLevel: List<String> = emptyList()
)

data class LauncherInfo(val versionName: String, val versionCode: Long, val apkPath: String)

data class ThemeCompatibilityReport(
    val rootAvailable: Boolean,
    val systemProperties: Map<String, String>,
    val paths: List<RootFileMetadata>,
    val archives: List<ThemeArchiveReport>,
    val launcher: LauncherInfo?,
    val monetColors: Map<String, String>,
    val adaptiveIconCount: Int,
    val monochromeIconCount: Int,
    val notes: List<String> = emptyList()
) {
    fun toText(): String = buildString {
        appendLine("HyperOS 3 Theme Compatibility Probe")
        appendLine("ROOT_READ_ONLY=$rootAvailable")
        appendLine("\n[SYSTEM_PROPERTIES]")
        systemProperties.forEach { (key, value) -> appendLine("$key=$value") }
        appendLine("\n[THEME_PATHS]")
        paths.forEach { item -> appendLine("${item.path}\ttype=${item.fileType}\tsize=${item.sizeBytes ?: "UNKNOWN"}\tsha256=${item.sha256 ?: "UNKNOWN"}\tuid=${item.uid ?: "UNKNOWN"}\tgid=${item.gid ?: "UNKNOWN"}\tmode=${item.mode ?: "UNKNOWN"}\tselinux=${item.selinuxContext ?: "UNKNOWN"}") }
        appendLine("\n[ARCHIVES]")
        archives.forEach { archive ->
            appendLine("${archive.path}\tZIP_COMPATIBLE=${archive.isZipCompatible}")
            appendLine("FLAGS=${archive.flags}")
            appendLine("TRANSFORM_CONFIG=${archive.transformConfigSummary}")
            appendLine("ENTRIES (${archive.entries.size}):")
            archive.entries.forEach(::appendLine)
            archive.packageDirectoryTrees.forEach { (dir, tree) -> appendLine("PACKAGE_TREE $dir: ${tree.joinToString()}") }
            archive.fancyIconTrees.forEach { (dir, tree) -> appendLine("FANCY_TREE $dir: ${tree.joinToString()}") }
            archive.fancyManifestSummaries.forEach { (path, summary) -> appendLine("FANCY_MANIFEST $path: $summary") }
            if (archive.dynamicTopLevel.isNotEmpty()) appendLine("DYNAMIC_TOP_LEVEL=${archive.dynamicTopLevel.joinToString()}")
        }
        appendLine("\n[LAUNCHER]\n${launcher ?: "NOT_FOUND"}")
        appendLine("\n[MONET]")
        monetColors.forEach { (key, value) -> appendLine("$key=$value") }
        appendLine("\n[ADAPTIVE_ICONS]\nAdaptiveIconDrawable=$adaptiveIconCount\ngetMonochrome_available=$monochromeIconCount")
        if (notes.isNotEmpty()) appendLine("\n[NOTES]\n${notes.joinToString("\n")}")
    }
}
