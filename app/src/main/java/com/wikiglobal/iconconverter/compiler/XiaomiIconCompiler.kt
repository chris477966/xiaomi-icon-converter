package com.wikiglobal.iconconverter.compiler

import com.wikiglobal.iconconverter.model.XiaomiIconEntry
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Creates Xiaomi's icons archive. Caller chooses a SAF OutputStream; this class never touches system theme paths. */
object XiaomiIconCompiler {
    const val DRAWABLE_DIR = "res/drawable-xxhdpi/"
    fun archivePaths(entries: List<XiaomiIconEntry>): List<String> {
        val grouped = entries.groupBy { it.packageName }
        return entries.flatMap { entry ->
            buildList {
                if (grouped.getValue(entry.packageName).first() === entry) add("$DRAWABLE_DIR${entry.packageName}.png")
                if (entry.requiresActivitySpecificPath) add("$DRAWABLE_DIR${entry.packageName}#${activityPart(entry.activityName, entry.packageName)}.png")
            }
        }.distinct()
    }
    fun writeIcons(output: OutputStream, entries: List<XiaomiIconEntry>) {
        ZipOutputStream(output).use { zip ->
            val written = mutableSetOf<String>()
            val firstByPackage = entries.groupBy { it.packageName }.mapValues { it.value.first() }
            entries.forEach { entry ->
                val destinations = buildList {
                    if (firstByPackage[entry.packageName] === entry) add("$DRAWABLE_DIR${entry.packageName}.png")
                    if (entry.requiresActivitySpecificPath) add("$DRAWABLE_DIR${entry.packageName}#${activityPart(entry.activityName, entry.packageName)}.png")
                }
                destinations.forEach { path -> if (written.add(path)) {
                    zip.putNextEntry(ZipEntry(path)); zip.write(entry.pngBytes); zip.closeEntry()
                } }
            }
        }
    }
    private fun activityPart(activity: String, packageName: String): String = activity.removePrefix("$packageName.")
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
}
