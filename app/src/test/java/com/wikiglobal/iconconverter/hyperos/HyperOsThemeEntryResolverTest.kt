package com.wikiglobal.iconconverter.hyperos

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class HyperOsThemeEntryResolverTest {
    @Test fun `only exact base and known launcher aliases are resolved`() {
        val archive = File(Files.createTempDirectory("aliases").toFile(), "icons")
        ZipOutputStream(archive.outputStream()).use { out -> listOf("pkg.png", "pkg.MainActivity.png", "pkg#SecondActivity.png", "pkg.other.png").forEach { name -> out.putNextEntry(ZipEntry("res/drawable-xxhdpi/$name")); out.write(byteArrayOf(1)); out.closeEntry() } }
        val index = HyperOsThemeArchiveIndex.from(archive)
        assertEquals(setOf("res/drawable-xxhdpi/pkg.png", "res/drawable-xxhdpi/pkg.MainActivity.png"), HyperOsThemeEntryResolver.resolve(index, LauncherComponentIdentity.from("pkg", "pkg.MainActivity", emptySet())).replacementEntries.toSet())
        assertEquals(setOf("res/drawable-xxhdpi/pkg.png", "res/drawable-xxhdpi/pkg#SecondActivity.png"), HyperOsThemeEntryResolver.resolve(index, LauncherComponentIdentity.from("pkg", "pkg.SecondActivity", emptySet())).replacementEntries.toSet())
    }
    @Test fun `DOT_RELATIVE_ACTIVITY`() { assertEquals("pkg.ui.LauncherUI", normalizeActivity("pkg", ".ui.LauncherUI")) }
    @Test fun `DIRECT_ACTIVITY`() { assertEquals("pkg.MainActivity", normalizeActivity("pkg", "MainActivity")) }
    @Test fun `HASH_RELATIVE`() { assertEquals("ui.LauncherUI", relativeActivity("pkg", "pkg.ui.LauncherUI")) }
    @Test fun `HASH_SIMPLE`() { assertEquals("LauncherUI", simpleActivity("pkg", "pkg.ui.LauncherUI")) }
}
