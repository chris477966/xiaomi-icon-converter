package com.wikiglobal.iconconverter.iconpack

import android.content.Context
import android.net.Uri
import com.wikiglobal.iconconverter.model.AppIconAssignment
import com.wikiglobal.iconconverter.model.IconAssignmentType
import com.wikiglobal.iconconverter.model.IconEntry
import com.wikiglobal.iconconverter.model.IconMatch
import com.wikiglobal.iconconverter.model.IconPack
import com.wikiglobal.iconconverter.model.InstalledApp
import com.wikiglobal.iconconverter.parser.IconPackParser
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.LinkedHashMap
import org.json.JSONArray
import org.json.JSONObject

/** Storage and resource loading boundary for icon packs. UI and matching do not know APK paths. */
class IconPackProvider(private val context: Context) {
    private val parser = IconPackParser(context)
    private val root = File(context.filesDir, "icon-packs")
    private val catalog = context.getSharedPreferences("icon-pack-catalog", Context.MODE_PRIVATE)
    private val loaded = LinkedHashMap<String, IconPack>()
    private val catalogCache = LinkedHashMap<String, JSONObject>()
    private var catalogLoaded = false

    fun import(uri: Uri): IconPack {
        root.mkdirs()
        val temp = File(root, "import-${System.nanoTime()}.tmp")
        context.contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(temp).use { output -> input.copyTo(output); output.fd.sync() } }
            ?: error("无法读取所选图标包")
        val parsed = runCatching { parser.parseApk(temp) }.getOrElse { temp.delete(); throw it }
        val destination = File(root, "${safe(parsed.packageName)}.apk")
        if (!temp.renameTo(destination)) {
            FileInputStream(temp).use { input -> FileOutputStream(destination).use { output -> input.copyTo(output); output.fd.sync() } }
            temp.delete()
        }
        val stable = parser.parseApk(destination)
        loaded[stable.id] = stable
        saveCatalog(stable, destination)
        setActiveId(stable.id)
        return stable
    }

    fun loadPersisted(): List<IconPack> {
        val result = mutableListOf<IconPack>()
        val packs = catalogArray()
        if (packs.isEmpty()) return emptyList()
        runCatching {
            for (item in packs) {
                val file = File(item.getString("path"))
                if (!file.isFile) continue
                val parsed = parser.parseApk(file)
                val signature = sha256(file)
                val pack = if (signature == item.optString("signature") && item.has("entries")) {
                    val entries = readEntries(item.getJSONArray("entries"), parsed.id)
                    parsed.copy(entries = entries, diagnostics = parsed.diagnostics.copy(entryCount = entries.size, resolvedResourceCount = entries.size))
                } else {
                    saveCatalog(parsed, file)
                    parsed
                }
                loaded[pack.id] = pack
                result += pack
            }
        }
        return result
    }

    fun loadedPacks(): List<IconPack> = loaded.values.toList()
    fun activeId(): String? = catalog.getString("activeId", null)
    fun setActiveId(id: String) { if (loaded.containsKey(id) || catalogArray().any { it.optString("id") == id }) catalog.edit().putString("activeId", id).apply() }

    fun delete(id: String) {
        val array = catalogArray().filterNot { it.optString("id") == id }
        catalogCache.clear()
        array.forEach { catalogCache[it.optString("id")] = it }
        catalogLoaded = true
        catalog.edit().putString("packs", JSONArray(array).toString()).apply()
        loaded.remove(id)
        if (activeId() == id) catalog.edit().remove("activeId").apply()
        File(root, "${safe(id)}.apk").delete()
    }

    fun sourceFile(id: String): File? = catalogArray().firstOrNull { it.optString("id") == id }?.optString("path")?.let(::File)

    private fun saveCatalog(pack: IconPack, file: File) {
        val next = catalogArray().filterNot { it.optString("id") == pack.id }.toMutableList()
        val record = JSONObject().apply {
            put("id", pack.id); put("packageName", pack.packageName); put("displayName", pack.displayName)
            put("versionName", pack.versionName); put("versionCode", pack.versionCode ?: JSONObject.NULL)
            put("path", file.absolutePath); put("signature", sha256(file))
            put("entryCount", pack.entries.size)
            put("entries", JSONArray(pack.entries.map { entry -> JSONObject().apply {
                put("iconPackId", entry.iconPackId); put("resourceName", entry.resourceName)
                put("resourceIdentifier", entry.resourceIdentifier); put("resourceType", entry.resourceType)
                put("mappedPackageNames", JSONArray(entry.mappedPackageNames.toList()))
                put("searchableKeywords", JSONArray(entry.searchableKeywords.toList()))
            } }))
        }
        next += record
        catalogCache.clear()
        next.forEach { catalogCache[it.optString("id")] = it }
        catalogLoaded = true
        catalog.edit().putString("packs", JSONArray(next).toString()).apply()
    }

    private fun readEntries(array: JSONArray, fallbackPackId: String): List<IconEntry> = (0 until array.length()).mapNotNull { index ->
        runCatching {
            val item = array.getJSONObject(index)
            IconEntry(
                iconPackId = item.optString("iconPackId", fallbackPackId),
                resourceName = item.getString("resourceName"),
                resourceIdentifier = item.optInt("resourceIdentifier", 0),
                resourceType = item.optString("resourceType", "drawable"),
                mappedPackageNames = item.optJSONArray("mappedPackageNames").toStringSet(),
                searchableKeywords = item.optJSONArray("searchableKeywords").toStringSet()
            )
        }.getOrNull()
    }
    private fun JSONArray?.toStringSet(): Set<String> = if (this == null) emptySet() else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }.toSet()
    private fun catalogArray(): List<JSONObject> = runCatching {
        if (!catalogLoaded) {
            catalogCache.clear()
            val array = JSONArray(catalog.getString("packs", "[]"))
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                catalogCache[item.optString("id")] = item
            }
            catalogLoaded = true
        }
        catalogCache.values.toList()
    }.getOrDefault(emptyList())
    private fun safe(value: String) = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
}

class AppIconAssignmentStore(context: Context) {
    private val prefs = context.getSharedPreferences("app-icon-assignments", Context.MODE_PRIVATE)
    private val cache = LinkedHashMap<String, AppIconAssignment>()
    private var loaded = false

    fun loadAll() {
        cache.clear()
        prefs.all.keys.filter { it.startsWith("assignment:") }.mapNotNull { key -> read(key.removePrefix("assignment:")) }.forEach { cache[it.appIdentifier] = it }
        loaded = true
    }

    fun get(appIdentifier: String): AppIconAssignment? {
        if (!loaded) loadAll()
        return cache[appIdentifier]
    }

    fun all(): List<AppIconAssignment> {
        if (!loaded) loadAll()
        return cache.values.toList()
    }

    private fun read(appIdentifier: String): AppIconAssignment? = runCatching {
        val value = prefs.getString(key(appIdentifier), null) ?: return null
        val json = JSONObject(value)
        AppIconAssignment(
            appIdentifier = json.getString("appIdentifier"), iconPackId = json.getString("iconPackId"),
            resourceName = json.getString("resourceName"),
            assignmentType = IconAssignmentType.valueOf(json.getString("assignmentType")),
            sourceAvailable = json.optBoolean("sourceAvailable", true),
            resourceIdentifier = json.optInt("resourceIdentifier", 0)
        )
    }.getOrNull()
    fun save(assignment: AppIconAssignment) {
        cache[assignment.appIdentifier] = assignment
        loaded = true
        prefs.edit().putString(key(assignment.appIdentifier), JSONObject().apply {
            put("appIdentifier", assignment.appIdentifier); put("iconPackId", assignment.iconPackId)
            put("resourceName", assignment.resourceName); put("assignmentType", assignment.assignmentType.name)
            put("sourceAvailable", assignment.sourceAvailable); put("resourceIdentifier", assignment.resourceIdentifier)
        }.toString()).apply()
    }
    fun remove(appIdentifier: String) { cache.remove(appIdentifier); loaded = true; prefs.edit().remove(key(appIdentifier)).apply() }
    fun removeForPack(iconPackId: String) { all().filter { it.iconPackId == iconPackId }.forEach { remove(it.appIdentifier) } }
    private fun key(appIdentifier: String) = "assignment:$appIdentifier"
}

object IconPackIndex {
    fun enrich(pack: IconPack, apps: List<InstalledApp>): IconPack {
        val labelsByPackage = apps.groupBy { it.packageName.lowercase() }.mapValues { (_, values) -> values.map { it.label }.toSet() }
        return pack.copy(entries = pack.entries.map { entry ->
            val labels = entry.mappedPackageNames.flatMap { labelsByPackage[it.lowercase()].orEmpty() }
            entry.copy(searchableKeywords = (entry.searchableKeywords + labels).filter { it.isNotBlank() }.toSet())
        })
    }

    fun search(entries: List<IconEntry>, query: String, currentApp: InstalledApp? = null): List<IconEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return entries
        val appPackage = currentApp?.packageName?.lowercase()
        val appLabel = currentApp?.label?.lowercase()
        fun score(entry: IconEntry): Int? {
            val resource = entry.resourceName.lowercase()
            val packages = entry.mappedPackageNames.map(String::lowercase)
            val keywords = entry.searchableKeywords.map(String::lowercase)
            val rank = when {
                appPackage != null && q == appPackage && appPackage in packages -> 0
                appLabel != null && q == appLabel && keywords.any { it == q } -> 1
                packages.any { it == q } -> 2
                keywords.any { it == q } -> 3
                packages.any { it.startsWith(q) } -> 4
                keywords.any { it.startsWith(q) } -> 5
                resource.startsWith(q) -> 6
                packages.any { it.contains(q) } -> 7
                keywords.any { it.contains(q) } || resource.contains(q) -> 8
                else -> null
            }
            return rank
        }
        return entries.mapNotNull { entry -> score(entry)?.let { it to entry } }
            .sortedWith(compareBy<Pair<Int, IconEntry>> { it.first }.thenBy { it.second.resourceName })
            .map { it.second }
    }
}

object IconAssignmentResolver {
    fun resolve(app: InstalledApp, activePack: IconPack, packs: Map<String, IconPack>, assignments: Map<String, AppIconAssignment>, legacy: IconMatch): IconMatch {
        val manual = assignments[app.packageName]
        if (manual?.assignmentType == IconAssignmentType.MANUAL && manual.sourceAvailable) {
            val pack = packs[manual.iconPackId]
            val entry = pack?.entries?.firstOrNull { it.resourceName == manual.resourceName && (manual.resourceIdentifier == 0 || it.resourceIdentifier == manual.resourceIdentifier) }
            if (entry != null) return legacy.copy(
                status = com.wikiglobal.iconconverter.model.MatchStatus.PACKAGE,
                confidence = com.wikiglobal.iconconverter.model.MatchConfidence.HIGH,
                drawableName = entry.resourceName, detail = "用户手动选择", assignmentType = IconAssignmentType.MANUAL,
                sourceIconPackId = pack.id, resourceIdentifier = entry.resourceIdentifier
            )
        }
        val entry = activePack.entries.firstOrNull { it.resourceName == legacy.drawableName }
        return legacy.copy(assignmentType = IconAssignmentType.AUTOMATIC, sourceIconPackId = activePack.id, resourceIdentifier = entry?.resourceIdentifier ?: legacy.resourceIdentifier)
    }
}
