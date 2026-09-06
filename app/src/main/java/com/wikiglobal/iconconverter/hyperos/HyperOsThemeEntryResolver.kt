package com.wikiglobal.iconconverter.hyperos

import java.io.File
import java.util.zip.ZipFile

data class LauncherComponentIdentity(
    val packageName: String,
    val launcherActivity: String,
    val equivalentActivities: Set<String>
) {
    companion object {
        fun from(packageName: String, launcherActivity: String, activityAliases: Set<String>): LauncherComponentIdentity {
            val normalizedLauncher = normalizeActivityClassName(packageName, launcherActivity)
            return LauncherComponentIdentity(packageName, normalizedLauncher, (activityAliases + launcherActivity)
                .map { normalizeActivityClassName(packageName, it) }.toSortedSet())
        }
    }
}

/** Full activity names are authoritative; relative/simple forms are legacy archive compatibility only. */
fun normalizeActivityClassName(packageName: String, activity: String): String {
    val value = activity.trim()
    return when {
        value.startsWith(".") -> packageName + value
        '.' !in value -> "$packageName.$value"
        else -> value
    }
}
fun relativeActivity(packageName: String, fullActivity: String): String? {
    val normalized = normalizeActivityClassName(packageName, fullActivity)
    return normalized.takeIf { it.startsWith("$packageName.") }?.removePrefix("$packageName.")
}
fun simpleActivity(packageName: String, fullActivity: String): String = normalizeActivityClassName(packageName, fullActivity).substringAfterLast('.')

/** Baseline archive is scanned once per Apply; it records names only and never decodes PNGs. */
class HyperOsThemeArchiveIndex private constructor(val entries: Set<String>) {
    fun contains(entryName: String): Boolean = entryName in entries
    companion object {
        fun from(archive: File): HyperOsThemeArchiveIndex = ZipFile(archive).use { zip ->
            HyperOsThemeArchiveIndex(zip.entries().asSequence().map { it.name }
                .filter { it.startsWith(HyperOs3ThemePatcher.DRAWABLE_PREFIX) && it.endsWith(".png") }.toSet())
        }
    }
}

data class ComponentThemeRoute(
    val packageName: String,
    val launcherActivity: String,
    val equivalentActivities: Set<String>,
    val packageEntry: String,
    val matchedActivityEntries: List<String>,
    val replacementEntries: List<String>
)

object HyperOsThemeEntryResolver {
    fun baseEntry(packageName: String) = "${HyperOs3ThemePatcher.DRAWABLE_PREFIX}$packageName.png"

    fun resolve(index: HyperOsThemeArchiveIndex, identity: LauncherComponentIdentity): ComponentThemeRoute {
        val packageEntry = baseEntry(identity.packageName)
        val activityEntries = identity.equivalentActivities.flatMap { full ->
            val relative = relativeActivity(identity.packageName, full)
            val simple = simpleActivity(identity.packageName, full)
            buildList {
                add("${HyperOs3ThemePatcher.DRAWABLE_PREFIX}$full.png")
                relative?.let { add("${HyperOs3ThemePatcher.DRAWABLE_PREFIX}${identity.packageName}#$it.png") }
                add("${HyperOs3ThemePatcher.DRAWABLE_PREFIX}${identity.packageName}#$simple.png")
                add("${HyperOs3ThemePatcher.DRAWABLE_PREFIX}${identity.packageName}.$simple.png")
            }
        }.distinct().filter(index::contains).sorted()
        return ComponentThemeRoute(identity.packageName, identity.launcherActivity, identity.equivalentActivities, packageEntry, activityEntries, listOf(packageEntry) + activityEntries)
    }
}
