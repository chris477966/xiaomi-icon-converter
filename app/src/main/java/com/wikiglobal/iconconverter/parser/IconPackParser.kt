package com.wikiglobal.iconconverter.parser

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import com.wikiglobal.iconconverter.model.IconEffects
import com.wikiglobal.iconconverter.model.IconEntry
import com.wikiglobal.iconconverter.model.IconPack
import com.wikiglobal.iconconverter.model.IconPackParseDiagnostics
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

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
            sourceDir = localApk.absolutePath
            publicSourceDir = localApk.absolutePath
        }
        val resources = packageManager.getResourcesForApplication(appInfo)
        val errors = mutableListOf<String>()
        fun recordError(message: String) {
            if (errors.size < MAX_DIAGNOSTIC_ERRORS && message !in errors) errors += message
        }

        fun resolveResource(name: String, preferredType: String? = null, recordResolutionError: Boolean = false): ResourceCandidate? {
            val normalized = normalizeResourceName(name) ?: return null
            val types = sequenceOf(preferredType, "drawable", "mipmap").filterNotNull().distinct()
            for (type in types) {
                val id = try {
                    resources.getIdentifier(normalized.name, type, archive.packageName)
                } catch (error: RuntimeException) {
                    if (recordResolutionError) recordError("getIdentifier $type/${normalized.name}: ${error.message ?: error.javaClass.simpleName}")
                    0
                }
                if (id == 0) continue
                val drawable = try {
                    @Suppress("DEPRECATION")
                    resources.getDrawable(id, null)
                } catch (error: RuntimeException) {
                    if (recordResolutionError) recordError("getDrawable $type/${normalized.name}: ${error.message ?: error.javaClass.simpleName}")
                    null
                }
                if (drawable != null) return ResourceCandidate(normalized.name, type, id)
            }
            return null
        }

        val appFilterSource = readXmlSource(localApk, "appfilter.xml", errors)
        val parsed = if (appFilterSource == null) {
            ParsedAppFilter(emptyList(), emptyList(), IconEffects())
        } else {
            try {
                // Keep every declared calendar drawable as a candidate. Resource validity is checked below.
                AppFilterParser.parse(ByteArrayInputStream(appFilterSource.bytes)) { true }
            } catch (error: Exception) {
                recordError("${appFilterSource.path}: ${error.message ?: error.javaClass.simpleName}")
                ParsedAppFilter(emptyList(), emptyList(), IconEffects())
            }
        }

        val diagnosticMappings = (parsed.mappings.take(10) + parsed.mappings.filter { it.component.packageName.equals("com.tencent.mm", ignoreCase = true) })
            .distinctBy { it.component.packageName to it.drawableName }
        diagnosticMappings.forEach { mapping ->
            val normalized = normalizeResourceName(mapping.drawableName)?.name ?: mapping.drawableName
            fun inspect(type: String): Pair<Int, Boolean> {
                val id = runCatching { resources.getIdentifier(normalized, type, archive.packageName) }.getOrDefault(0)
                val loaded = id != 0 && runCatching {
                    @Suppress("DEPRECATION")
                    resources.getDrawable(id, null)
                }.getOrNull() != null
                return id to loaded
            }
            val drawable = inspect("drawable")
            val mipmap = inspect("mipmap")
            Log.i(TAG, "mapping package=${mapping.component.packageName} drawableName=$normalized drawableId=${drawable.first} mipmapId=${mipmap.first} drawableLoaded=${drawable.second} mipmapLoaded=${mipmap.second}")
        }

        val drawableXml = discoverXmlResources(localApk, "drawable.xml", errors)
        val iconPackXml = discoverXmlResources(localApk, "iconpack.xml", errors)
        val zipDiscovery = discoverZipResources(localApk, errors)

        val candidates = linkedMapOf<String, NamedResourceCandidate>()
        fun addCandidate(candidate: NamedResourceCandidate) {
            val normalizedName = normalizeResourceName(candidate.name)?.name ?: return
            val normalized = NamedResourceCandidate(normalizedName, candidate.type)
            val key = "${candidate.type ?: "*"}:${normalized.name}"
            candidates.putIfAbsent(key, normalized)
        }
        drawableXml.names.forEach(::addCandidate)
        iconPackXml.names.forEach(::addCandidate)
        parsed.mappings.forEach { addCandidate(NamedResourceCandidate(it.drawableName, null)) }
        parsed.calendars.flatMap { it.dayDrawables.values }.forEach { addCandidate(NamedResourceCandidate(it, null)) }
        zipDiscovery.names.forEach(::addCandidate)

        val mappedPackages = parsed.mappings.groupBy { normalizeResourceName(it.drawableName)?.name.orEmpty() }
            .mapValues { (_, mappings) -> mappings.map { it.component.packageName }.toSet() }
        val entries = candidates.values.mapNotNull { candidate ->
            val resolved = resolveResource(candidate.name, candidate.type, true) ?: return@mapNotNull null
            IconEntry(
                iconPackId = archive.packageName,
                resourceName = resolved.name,
                resourceIdentifier = resolved.id,
                resourceType = resolved.type,
                mappedPackageNames = mappedPackages[resolved.name].orEmpty(),
                searchableKeywords = setOf(resolved.name) + mappedPackages[resolved.name].orEmpty()
            )
        }.distinctBy { it.resourceType to it.resourceName }.sortedBy { it.resourceName }

        val diagnostics = IconPackParseDiagnostics(
            appFilterPresent = appFilterSource != null,
            mappingCount = parsed.mappings.size,
            calendarMappingCount = parsed.calendars.size,
            drawableXmlPresent = drawableXml.present,
            drawableXmlIconCount = drawableXml.names.size,
            iconPackXmlPresent = iconPackXml.present,
            iconPackXmlIconCount = iconPackXml.names.size,
            zipCandidateCount = zipDiscovery.names.size,
            resolvedResourceCount = entries.size,
            entryCount = entries.size,
            errors = errors.toList()
        )
        Log.i(TAG, "Icon pack ${archive.packageName}: ${diagnostics.summary}")
        diagnostics.errors.forEach { Log.w(TAG, "Icon pack ${archive.packageName}: $it") }
        if (diagnostics.resourceIndexFailed) {
            Log.e(TAG, "Icon pack ${archive.packageName}: mappings exist but resource index is empty")
        }

        val label = runCatching {
            if (appInfo.labelRes != 0) resources.getString(appInfo.labelRes) else appInfo.nonLocalizedLabel?.toString()
        }.getOrNull().orEmpty().ifBlank { archive.packageName }
        val versionName = archive.versionName?.toString()?.takeIf { it.isNotBlank() }
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) archive.longVersionCode else archive.versionCode.toLong()
        val loader: (String) -> Drawable? = { name ->
            resolveResource(name)?.let { resolved ->
                @Suppress("DEPRECATION")
                runCatching { resources.getDrawable(resolved.id, null) }.getOrNull()
            }
        }
        return IconPack(
            displayName = label,
            packageName = archive.packageName,
            mappings = parsed.mappings,
            calendars = parsed.calendars,
            effects = parsed.effects,
            drawableLoader = loader,
            id = archive.packageName,
            versionName = versionName,
            versionCode = versionCode,
            entries = entries,
            resourceLoader = { id ->
                @Suppress("DEPRECATION")
                runCatching { resources.getDrawable(id, null) }.getOrNull()
            },
            diagnostics = diagnostics
        )
    }

    private data class ResourceCandidate(val name: String, val type: String, val id: Int)
    private data class NamedResourceCandidate(val name: String, val type: String?)
    private data class XmlResourceDiscovery(val present: Boolean, val names: Set<NamedResourceCandidate>)
    private data class XmlSource(val path: String, val bytes: ByteArray)
    private data class ZipResourceDiscovery(val names: Set<NamedResourceCandidate>)

    private fun readXmlSource(apk: File, name: String, errors: MutableList<String>): XmlSource? {
        val paths = listOf(name, "assets/$name", "res/raw/$name", "res/xml/$name")
        return try {
            ZipFile(apk).use { zip ->
                val entry = zip.entries().asSequence().firstOrNull { candidate ->
                    paths.any { it.equals(candidate.name, ignoreCase = true) }
                } ?: return@use null
                XmlSource(entry.name, zip.getInputStream(entry).use { it.readBytes() })
            }
        } catch (error: Exception) {
            recordDiagnostic(errors, "$name ZIP read: ${error.message ?: error.javaClass.simpleName}")
            null
        }
    }

    private fun discoverXmlResources(apk: File, name: String, errors: MutableList<String>): XmlResourceDiscovery {
        val source = readXmlSource(apk, name, errors) ?: return XmlResourceDiscovery(false, emptySet())
        return try {
            val document = secureDocumentBuilderFactory().newDocumentBuilder().parse(ByteArrayInputStream(source.bytes))
            val names = linkedSetOf<NamedResourceCandidate>()
            val nodes = document.getElementsByTagName("*")
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                val tag = element.tagName.substringAfterLast(':').lowercase()
                val resourceTag = tag in RESOURCE_TAGS
                for (attributeIndex in 0 until element.attributes.length) {
                    val attribute = element.attributes.item(attributeIndex)
                    val attributeName = attribute.nodeName.substringAfterLast(':').lowercase()
                    val value = attribute.nodeValue?.trim().orEmpty()
                    val reference = resourceReference(value)
                    if (reference != null) {
                        names += reference
                    } else if (resourceTag && attributeName in RESOURCE_ATTRIBUTES) {
                        normalizeResourceName(value)?.let { names += NamedResourceCandidate(it.name, null) }
                    }
                }
                if (resourceTag && element.attributes.length == 0) {
                    normalizeResourceName(element.textContent?.trim().orEmpty())?.let { names += NamedResourceCandidate(it.name, null) }
                }
            }
            XmlResourceDiscovery(true, names)
        } catch (error: Exception) {
            recordDiagnostic(errors, "${source.path}: ${error.message ?: error.javaClass.simpleName}")
            XmlResourceDiscovery(true, emptySet())
        }
    }

    private fun discoverZipResources(apk: File, errors: MutableList<String>): ZipResourceDiscovery {
        val candidates = linkedSetOf<NamedResourceCandidate>()
        try {
            ZipFile(apk).use { zip ->
                zip.entries().asSequence().map { it.name }.forEach { path ->
                    val parts = path.split('/')
                    if (parts.size != 3 || parts[0] != "res") return@forEach
                    val type = parts[1].substringBefore('-')
                    if (type != "drawable" && type != "mipmap") return@forEach
                    val raw = parts[2].substringBeforeLast('.')
                    val name = raw.removeSuffix(".9")
                    if (name.isNotBlank()) candidates += NamedResourceCandidate(name, type)
                }
            }
        } catch (error: Exception) {
            recordDiagnostic(errors, "ZIP resource scan: ${error.message ?: error.javaClass.simpleName}")
        }
        return ZipResourceDiscovery(candidates)
    }

    private fun resourceReference(value: String): NamedResourceCandidate? {
        val normalized = value.removePrefix("@").removePrefix("+")
        val slash = normalized.indexOf('/')
        if (slash <= 0) return null
        val type = normalized.substring(0, slash).lowercase()
        if (type != "drawable" && type != "mipmap") return null
        return normalizeResourceName(normalized.substring(slash + 1))?.let { NamedResourceCandidate(it.name, type) }
    }

    private fun normalizeResourceName(raw: String): NamedResourceCandidate? {
        var value = raw.trim().removePrefix("@drawable/").removePrefix("@mipmap/")
            .removePrefix("drawable/").removePrefix("mipmap/")
        if (value.isBlank()) return null
        value = value.substringAfterLast('/').substringBeforeLast('.').removeSuffix(".9")
        return value.takeIf { it.isNotBlank() }?.let { NamedResourceCandidate(it, null) }
    }

    private fun secureDocumentBuilderFactory(): DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
    }

    private fun recordDiagnostic(errors: MutableList<String>, message: String) {
        if (errors.size < MAX_DIAGNOSTIC_ERRORS && message !in errors) errors += message
    }

    private companion object {
        const val TAG = "IconPackParser"
        const val MAX_DIAGNOSTIC_ERRORS = 20
        val RESOURCE_TAGS = setOf("item", "icon", "drawable", "resource", "iconitem", "entry")
        val RESOURCE_ATTRIBUTES = setOf("name", "drawable", "icon", "resource", "resourcename", "image", "src", "file")
    }
}
