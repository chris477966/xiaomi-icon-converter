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
        assertEquals(setOf("res/drawable-xxhdpi/pkg.png", "res/drawable-xxhdpi/pkg.MainActivity.png"), HyperOsThemeEntryResolver.entriesFor(archive, "pkg", "pkg.MainActivity"))
        assertEquals(setOf("res/drawable-xxhdpi/pkg.png", "res/drawable-xxhdpi/pkg#SecondActivity.png"), HyperOsThemeEntryResolver.entriesFor(archive, "pkg", "pkg.SecondActivity"))
    }
}
