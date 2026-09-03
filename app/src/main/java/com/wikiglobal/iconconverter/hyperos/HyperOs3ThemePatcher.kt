package com.wikiglobal.iconconverter.hyperos

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Patches the verified HyperOS 3 archive format without inventing legacy 0.png/1.png entries. */
data class HyperOs3IconReplacement(val packageName: String, val png: ByteArray, val activitySuffix: String? = null) {
    val entryName: String get() = "res/drawable-xxhdpi/" + packageName + (activitySuffix?.let { "#$it" } ?: "") + ".png"
}

data class HyperOs3PatchResult(val entryCount: Int, val replacementCount: Int, val preservedEntrySha256: Map<String, String>)

object HyperOs3ThemePatcher {
    const val DRAWABLE_PREFIX = "res/drawable-xxhdpi/"
    const val ICON_SIZE = 250

    fun patch(baseArchive: File, patchedArchive: File, replacements: List<HyperOs3IconReplacement>): HyperOs3PatchResult {
        require(replacements.isNotEmpty()) { "No icon replacements requested" }
        val targetNames = replacements.associateBy { it.entryName }
        require(targetNames.size == replacements.size) { "Duplicate replacement entry" }
        replacements.forEach { require(PngValidator.validate(it.png).isValidIcon) { "Invalid replacement ${it.entryName}" } }
        val seen = linkedSetOf<String>()
        val preserved = linkedMapOf<String, String>()
        ZipFile(baseArchive).use { source ->
            ZipOutputStream(FileOutputStream(patchedArchive)).use { out ->
                val entries = source.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    require(seen.add(entry.name)) { "Duplicate source entry: ${entry.name}" }
                    if (entry.name in targetNames) continue
                    val copy = ZipEntry(entry.name).also { it.time = entry.time }
                    out.putNextEntry(copy)
                    if (!entry.isDirectory) source.getInputStream(entry).use { input ->
                        val bytes = input.readBytes(); out.write(bytes)
                        if (entry.name == "transform_config.xml" || entry.name.startsWith("fancy_icons/")) preserved[entry.name] = sha256(bytes)
                    }
                    out.closeEntry()
                }
                targetNames.toSortedMap().forEach { (name, replacement) ->
                    out.putNextEntry(ZipEntry(name)); out.write(replacement.png); out.closeEntry()
                }
            }
        }
        val verified = validatePatchedArchive(patchedArchive, targetNames.keys)
        require(verified) { "Patched archive validation failed" }
        return HyperOs3PatchResult(ZipFile(patchedArchive).use { it.size() }, replacements.size, preserved)
    }

    fun validatePatchedArchive(archive: File, requiredEntries: Set<String>): Boolean = runCatching {
        val seen = mutableSetOf<String>(); val found = mutableSetOf<String>()
        ZipInputStream(FileInputStream(archive)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                check(seen.add(entry.name)) { "Duplicate ZIP entry: ${entry.name}" }
                if (entry.name in requiredEntries) {
                    val bytes = input.readBytes(); check(bytes.isNotEmpty()); check(PngValidator.validate(bytes).isValidIcon); found += entry.name
                }
                input.closeEntry()
            }
        }
        found == requiredEntries && seen.isNotEmpty()
    }.getOrDefault(false)

    fun sha256(file: File): String = FileInputStream(file).use { sha256(it.readBytes()) }
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

data class PngValidation(val width: Int?, val height: Int?, val hasAlpha: Boolean, val isValidIcon: Boolean)
/** Header-level validation remains JVM-testable; Android decoding is additionally performed by the renderer before patching. */
object PngValidator {
    private val signature = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
    fun validate(bytes: ByteArray): PngValidation {
        if (bytes.size < 33 || !bytes.copyOfRange(0, 8).contentEquals(signature)) return PngValidation(null, null, false, false)
        fun intAt(offset: Int) = ((bytes[offset].toInt() and 255) shl 24) or ((bytes[offset + 1].toInt() and 255) shl 16) or ((bytes[offset + 2].toInt() and 255) shl 8) or (bytes[offset + 3].toInt() and 255)
        val width = intAt(16); val height = intAt(20); val alpha = bytes[25].toInt() == 4 || bytes[25].toInt() == 6
        return PngValidation(width, height, alpha, width == HyperOs3ThemePatcher.ICON_SIZE && height == HyperOs3ThemePatcher.ICON_SIZE && alpha && bytes.size > 40)
    }
}
