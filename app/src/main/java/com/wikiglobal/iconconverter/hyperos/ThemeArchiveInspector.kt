package com.wikiglobal.iconconverter.hyperos

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Pure archive-layout inspection; it reports real names rather than applying a HyperOS 1/2 template. */
object ThemeArchiveInspector {
    fun inspect(path: String, entries: List<String>, xmlReader: (String) -> ByteArray?): ThemeArchiveReport {
        val normalized = entries.map { it.trimStart('/') }.filter { it.isNotBlank() }
        val lower = normalized.map { it.lowercase() }
        fun contains(part: String) = lower.any { it.contains(part) }
        val packageDirs = normalized.filter { it.startsWith("res/drawable-xxhdpi/") && it.removePrefix("res/drawable-xxhdpi/").contains('/') }
            .map { "res/drawable-xxhdpi/" + it.removePrefix("res/drawable-xxhdpi/").substringBefore('/') }.distinct().take(3)
        val fancyRoots = normalized.filter { it.lowercase().contains("fancy_icons/") }
            .map { it.substringBefore("fancy_icons/") + "fancy_icons/" + it.substringAfter("fancy_icons/").substringBefore('/') }.distinct().take(3)
        val transform = normalized.firstOrNull { it.substringAfterLast('/').equals("transform_config.xml", true) }
        val manifests = normalized.filter { it.substringAfterLast('/').equals("manifest.xml", true) && it.lowercase().contains("fancy") }.take(3)
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
            flags = flags,
            transformConfigSummary = transform?.let { xmlReader(it)?.let(::xmlSummary) ?: "READ_FAILED" } ?: "NOT_FOUND",
            packageDirectoryTrees = packageDirs.associateWith { dir -> normalized.filter { it.startsWith("$dir/") }.take(64) },
            fancyIconTrees = fancyRoots.associateWith { dir -> normalized.filter { it.startsWith("$dir/") }.take(64) },
            fancyManifestSummaries = manifests.associateWith { xmlReader(it)?.let(::xmlSummary) ?: "READ_FAILED" },
            dynamicTopLevel = normalized.filter { it.lowercase().contains("dynamicicons") }.map { entry ->
                val end = entry.lowercase().indexOf("dynamicicons") + "dynamicicons".length
                entry.substring(0, end).trim('/')
            }.distinct().take(64)
        )
    }

    fun xmlSummary(bytes: ByteArray): String = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        val nodes = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).getElementsByTagName("*")
        (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }.take(32).joinToString(" > ") { element ->
            val attrs = (0 until element.attributes.length).joinToString(",") { element.attributes.item(it).nodeName }
            if (attrs.isBlank()) element.tagName else "${element.tagName}[$attrs]"
        }
    }.getOrElse { "PARSE_FAILED:${it.javaClass.simpleName}" }
}
