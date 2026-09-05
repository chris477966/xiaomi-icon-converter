package com.wikiglobal.iconconverter.hyperos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class HyperOs3ThemePatcherTest {
    @Test fun `patch uses HyperOS 3 package png and preserves transform fancy and unrelated entries`() {
        val dir = Files.createTempDirectory("hyperos-patch").toFile(); val base = File(dir, "base.zip"); val patched = File(dir, "patched.zip")
        val transform = "<IconTransform/>".toByteArray(); val fancy = "calendar-data".toByteArray()
        zip(base, mapOf("transform_config.xml" to transform, "fancy_icons/com.android.calendar/manifest.xml" to fancy, "res/drawable-xxhdpi/old.app.png" to png(), "res/drawable-xxhdpi/untouched.png" to png()))
        val result = HyperOs3ThemePatcher.patch(base, patched, listOf(HyperOs3IconReplacement("com.example.app", png())))
        ZipFile(patched).use { zip ->
            assertTrue(zip.getEntry("res/drawable-xxhdpi/com.example.app.png") != null)
            assertFalse(zip.entries().toList().any { it.name.endsWith("/0.png") || it.name.endsWith("/1.png") })
            assertTrue(zip.getInputStream(zip.getEntry("transform_config.xml")).readBytes().contentEquals(transform))
            assertTrue(zip.getInputStream(zip.getEntry("fancy_icons/com.android.calendar/manifest.xml")).readBytes().contentEquals(fancy))
        }
        assertEquals(5, result.entryCount); assertTrue(HyperOs3ThemePatcher.validatePatchedArchive(patched, setOf("res/drawable-xxhdpi/com.example.app.png")))
    }
    @Test fun `png validator validates requested alpha icon format`() { assertTrue(PngValidator.validate(png(), 250).isValidIcon); assertFalse(PngValidator.validate(ByteArray(0), 250).isValidIcon) }
    @Test fun `same package distinct activities retain package and activity specific paths`() {
        val dir = Files.createTempDirectory("activity-patch").toFile(); val base = File(dir, "base.zip"); val patched = File(dir, "patched.zip")
        zip(base, mapOf("transform_config.xml" to "<IconTransform/>".toByteArray(), "fancy_icons/com.android.calendar/manifest.xml" to "kept".toByteArray()))
        HyperOs3ThemePatcher.patch(base, patched, listOf(HyperOs3IconReplacement("com.example", png()), HyperOs3IconReplacement("com.example", png(), "SecondActivity")))
        ZipFile(patched).use { zip -> assertTrue(zip.getEntry("res/drawable-xxhdpi/com.example.png") != null); assertTrue(zip.getEntry("res/drawable-xxhdpi/com.example#SecondActivity.png") != null) }
    }
    private fun zip(file: File, contents: Map<String, ByteArray>) = ZipOutputStream(file.outputStream()).use { out -> contents.forEach { (name, bytes) -> out.putNextEntry(ZipEntry(name)); out.write(bytes); out.closeEntry() } }
    private fun png(): ByteArray = ByteArray(48).also { b -> byteArrayOf(-119,80,78,71,13,10,26,10).copyInto(b); b[12]=73;b[13]=72;b[14]=68;b[15]=82;b[16]=0;b[17]=0;b[18]=0;b[19]=-6;b[20]=0;b[21]=0;b[22]=0;b[23]=-6;b[24]=8;b[25]=6 }
    private fun <T> java.util.Enumeration<T>.toList(): List<T> = buildList { while (hasMoreElements()) add(nextElement()) }
}
