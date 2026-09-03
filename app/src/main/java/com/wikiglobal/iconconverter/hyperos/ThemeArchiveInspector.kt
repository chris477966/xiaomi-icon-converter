package com.wikiglobal.iconconverter.hyperos

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Pure archive-layout inspection; it reports real names rather than applying a HyperOS 1/2 template. */
object ThemeArchiveInspector {
    fun inspect(path: String, entries: List<String>, entryReader: (String) -> ByteArray?): ThemeArchiveReport {
        val normalized = entries.map { it.trimStart('/') }.filter { it.isNotBlank() }
        val lower = normalized.map { it.lowercase() }
        fun contains(part: String) = lower.any { it.contains(part) }
        val packageDirs = normalized.filter { it.startsWith("res/drawable-xxhdpi/") && it.removePrefix("res/drawable-xxhdpi/").contains('/') }
            .map { "res/drawable-xxhdpi/" + it.removePrefix("res/drawable-xxhdpi/").substringBefore('/') }.distinct().take(3)
        val fancyRoots = normalized.filter { it.lowercase().contains("fancy_icons/") }
            .map { it.substringBefore("fancy_icons/") + "fancy_icons/" + it.substringAfter("fancy_icons/").substringBefore('/') }.distinct().take(3)
        val transform = normalized.firstOrNull { it.substringAfterLast('/').equals("transform_config.xml", true) }
        val manifests = normalized.filter { it.substringAfterLast('/').equals("manifest.xml", true) && it.lowercase().contains("fancy") }.take(3)
        val sampleNames = linkedSetOf<String>()
        normalized.filter { it.startsWith("res/drawable-xxhdpi/") && it.endsWith(".png", true) }.forEach { entry ->
            val rest = entry.removePrefix("res/drawable-xxhdpi/")
            sampleNames += if ('/' in rest) rest.substringBefore('/') else rest.removeSuffix(".png")
        }
        val flags = ArchiveFlags(
            hasTransformConfig = transform != null,
            hasDrawableXxhdpi = lower.any { it.startsWith("res/drawable-xxhdpi/") },
            hasPackageDirectories = packageDirs.isNotEmpty(),
            hasPackagePng = lower.any { it.startsWith("res/drawable-xxhdpi/") && it.endsWith(".png") },
            hasLayer0Png = lower.any { it.endsWith("/0.png") },
            hasLayer1Png = lower.any { it.endsWith("/1.png") },
            hasFancyIcons = contains("fancy_icons"),
            hasFancyManifest = manifests.isNotEmpty(),
            hasDynamicIcons = contains("dynamicicons"),
            hasLayerAnimatingIcons = contains("layer_animating_icons") || contains("animating_icons")
        )
        return ThemeArchiveReport(
            path = path,
            isZipCompatible = true,
            entries = normalized,
            topLevelPaths = normalized.map { it.substringBefore('/') }.distinct(),
            flags = flags,
            transformConfigSummary = transform?.let { entryReader(it)?.let(::xmlSummary) ?: "READ_FAILED" } ?: "NOT_FOUND",
            packageDirectoryTrees = packageDirs.associateWith { dir -> normalized.filter { it.startsWith("$dir/") }.take(64) },
            fancyIconTrees = fancyRoots.associateWith { dir -> normalized.filter { it.startsWith("$dir/") }.take(64) },
            fancyManifestSummaries = manifests.associateWith { entryReader(it)?.let(::xmlSummary) ?: "READ_FAILED" },
            dynamicTopLevel = normalized.filter { it.lowercase().contains("dynamicicons") }.map { entry ->
                val end = entry.lowercase().indexOf("dynamicicons") + "dynamicicons".length
                entry.substring(0, end).trim('/')
            }.distinct().take(64),
            packageSamples = sampleNames.take(5).map { packageName ->
                val prefix = "res/drawable-xxhdpi/$packageName"
                val tree = normalized.filter { it == "$prefix.png" || it.startsWith("$prefix/") }
                val layered = tree.any { it.endsWith("/0.png", true) || it.endsWith("/1.png", true) }
                IconPackageSample(packageName, if (layered) "LAYERED_0_1" else if (tree.any { it == "$prefix.png" }) "PACKAGE_PNG" else "OTHER", tree, tree.filter { it.endsWith(".png", true) }.take(8).map { pngMetadata(it, entryReader(it)) })
            }
        )
    }

    private fun pngMetadata(entry: String, bytes: ByteArray?): PngMetadata {
        val valid = bytes != null && bytes.size >= 26 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))
        if (!valid) return PngMetadata(entry, null, null, "UNREADABLE", "UNKNOWN", bytes?.size)
        fun intAt(offset: Int) = ((bytes!![offset].toInt() and 0xff) shl 24) or ((bytes[offset + 1].toInt() and 0xff) shl 16) or ((bytes[offset + 2].toInt() and 0xff) shl 8) or (bytes[offset + 3].toInt() and 0xff)
        val color = bytes!![25].toInt() and 0xff
        val colorName = mapOf(0 to "GRAYSCALE", 2 to "TRUECOLOR", 3 to "INDEXED", 4 to "GRAYSCALE_ALPHA", 6 to "TRUECOLOR_ALPHA")[color] ?: "UNKNOWN($color)"
        return PngMetadata(entry, intAt(16), intAt(20), colorName, if (color == 4 || color == 6) "YES" else "NO", bytes.size)
    }

    fun xmlSummary(bytes: ByteArray): String = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        val nodes = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).getElementsByTagName("*")
        val elements = (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }.take(32)
        val root = elements.firstOrNull() ?: return "EMPTY_XML"
        val attrs = (0 until root.attributes.length).joinToString(",") { root.attributes.item(it).nodeName }
        "root=${root.tagName}; attributes=${if (attrs.isBlank()) "NONE" else attrs}; childTags=${elements.drop(1).map { it.tagName }.distinct().joinToString(",").ifBlank { "NONE" }}"
    }.getOrElse { "PARSE_FAILED:${it.javaClass.simpleName}" }
}
