package com.wikiglobal.iconconverter.hyperos

import java.io.File
import java.util.zip.ZipFile

/** Resolves aliases only when their exact existing archive entry is known. */
object HyperOsThemeEntryResolver {
    fun entriesFor(archive: File, packageName: String, launcherActivity: String): Set<String> = ZipFile(archive).use { zip ->
        val part = activityPart(launcherActivity, packageName)
        val candidates = listOf(
            "${HyperOs3ThemePatcher.DRAWABLE_PREFIX}$packageName.png",
            "${HyperOs3ThemePatcher.DRAWABLE_PREFIX}$packageName#$part.png",
            "${HyperOs3ThemePatcher.DRAWABLE_PREFIX}$packageName.$part.png"
        )
        candidates.filterTo(linkedSetOf()) { zip.getEntry(it) != null }
    }
    fun baseEntry(packageName: String) = "${HyperOs3ThemePatcher.DRAWABLE_PREFIX}$packageName.png"
    private fun activityPart(activity: String, pkg: String) = activity.removePrefix(pkg).removePrefix(".").substringAfterLast('.')
}
