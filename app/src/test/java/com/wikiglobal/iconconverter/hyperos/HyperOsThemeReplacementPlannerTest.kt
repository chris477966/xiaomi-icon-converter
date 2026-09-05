package com.wikiglobal.iconconverter.hyperos

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class HyperOsThemeReplacementPlannerTest {
    @Test fun `SINGLE_ACTIVITY_EXISTING_ALIAS_IS_REPLACED`() {
        val dir = Files.createTempDirectory("single-alias").toFile(); val base = File(dir, "base"); val patched = File(dir, "patched")
        zip(base, mapOf("res/drawable-xxhdpi/pkg.png" to png(1), "res/drawable-xxhdpi/pkg.MainActivity.png" to png(2), "res/drawable-xxhdpi/pkg.other.png" to png(3)))
        HyperOs3ThemePatcher.patch(base, patched, HyperOsThemeReplacementPlanner.plan(base, listOf(RenderedThemeActivityIcon("pkg", "pkg.MainActivity", png(9)))), 250)
        ZipFile(patched).use { zip ->
            assertEntry(zip, "res/drawable-xxhdpi/pkg.png", png(9)); assertEntry(zip, "res/drawable-xxhdpi/pkg.MainActivity.png", png(9)); assertEntry(zip, "res/drawable-xxhdpi/pkg.other.png", png(3))
        }
    }

    @Test fun `MULTI_ACTIVITY_EXACT_ALIASES_AND_UNRELATED_ALIAS_UNCHANGED`() {
        val dir = Files.createTempDirectory("multi-alias").toFile(); val base = File(dir, "base"); val patched = File(dir, "patched")
        zip(base, mapOf("res/drawable-xxhdpi/pkg.png" to png(1), "res/drawable-xxhdpi/pkg.MainActivity.png" to png(2), "res/drawable-xxhdpi/pkg#SecondActivity.png" to png(3), "res/drawable-xxhdpi/pkg.other.png" to png(4)))
        val plan = HyperOsThemeReplacementPlanner.plan(base, listOf(RenderedThemeActivityIcon("pkg", "pkg.MainActivity", png(9)), RenderedThemeActivityIcon("pkg", "pkg.SecondActivity", png(8))))
        HyperOs3ThemePatcher.patch(base, patched, plan, 250)
        ZipFile(patched).use { zip ->
            assertEntry(zip, "res/drawable-xxhdpi/pkg.MainActivity.png", png(9)); assertEntry(zip, "res/drawable-xxhdpi/pkg#SecondActivity.png", png(8)); assertEntry(zip, "res/drawable-xxhdpi/pkg.other.png", png(4))
        }
    }
    private fun assertEntry(zip: ZipFile, name: String, expected: ByteArray) = assertTrue(zip.getInputStream(zip.getEntry(name)).readBytes().contentEquals(expected))
    private fun zip(file: File, contents: Map<String, ByteArray>) = ZipOutputStream(file.outputStream()).use { out -> contents.forEach { (name, bytes) -> out.putNextEntry(ZipEntry(name)); out.write(bytes); out.closeEntry() } }
    private fun png(marker:Int)=ByteArray(48).also { b -> byteArrayOf(-119,80,78,71,13,10,26,10).copyInto(b);b[12]=73;b[13]=72;b[14]=68;b[15]=82;b[17]=0;b[18]=0;b[19]=-6;b[21]=0;b[22]=0;b[23]=-6;b[24]=8;b[25]=6;b[47]=marker.toByte() }
}
