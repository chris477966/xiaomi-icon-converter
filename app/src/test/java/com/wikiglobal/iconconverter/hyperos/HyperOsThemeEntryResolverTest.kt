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
        assertEquals(setOf("res/drawable-xxhdpi/pkg.png", "res/drawable-xxhdpi/pkg.SecondActivity.png", "res/drawable-xxhdpi/pkg#SecondActivity.png"), HyperOsThemeEntryResolver.resolve(index, LauncherComponentIdentity.from("pkg", "pkg.SecondActivity", emptySet())).replacementEntries.toSet())
    }
    @Test fun `DOT_RELATIVE_ACTIVITY`() { assertEquals("pkg.ui.LauncherUI", normalizeActivityClassName("pkg", ".ui.LauncherUI")) }
    @Test fun `SIMPLE_ACTIVITY`() { assertEquals("pkg.MainActivity", normalizeActivityClassName("pkg", "MainActivity")) }
    @Test fun `HASH_RELATIVE`() { assertEquals("ui.LauncherUI", relativeActivity("pkg", "pkg.ui.LauncherUI")) }
    @Test fun `HASH_SIMPLE`() { assertEquals("LauncherUI", simpleActivity("pkg", "pkg.ui.LauncherUI")) }
    @Test fun `FOREIGN_NAMESPACE_FULL_ACTIVITY_PRESERVED`() { assertEquals("org.chromium.chrome.browser.ChromeTabbedActivity", normalizeActivityClassName("com.android.chrome", "org.chromium.chrome.browser.ChromeTabbedActivity")) }
    @Test fun `FOREIGN_NAMESPACE_THEME_ENTRY_MATCHED`() {
        val archive=File(Files.createTempDirectory("foreign").toFile(),"icons");ZipOutputStream(archive.outputStream()).use{out->out.putNextEntry(ZipEntry("res/drawable-xxhdpi/org.chromium.chrome.browser.ChromeTabbedActivity.png"));out.write(byteArrayOf(1));out.closeEntry()}
        val route=HyperOsThemeEntryResolver.resolve(HyperOsThemeArchiveIndex.from(archive),LauncherComponentIdentity.from("com.android.chrome","org.chromium.chrome.browser.ChromeTabbedActivity",emptySet()))
        assertEquals(listOf("res/drawable-xxhdpi/org.chromium.chrome.browser.ChromeTabbedActivity.png"),route.matchedActivityEntries)
    }
    @Test fun `FOREIGN_NAMESPACE_NOT_PACKAGE_PREFIXED`() { assertEquals(null, relativeActivity("com.android.chrome", "org.chromium.chrome.browser.ChromeTabbedActivity")) }
    @Test fun `PACKAGE_NAMESPACE_ACTIVITY_UNCHANGED`() { assertEquals("com.tencent.mm.ui.LauncherUI", normalizeActivityClassName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI")) }
    @Test fun `TARGET_ACTIVITY_FOREIGN_NAMESPACE`() {
        val identity=LauncherComponentIdentity.from("com.foo","com.foo.alias.Home",setOf("org.vendor.real.HomeActivity"))
        assertEquals(setOf("com.foo.alias.Home","org.vendor.real.HomeActivity"),identity.activities.map { it.activity }.toSet())
    }
}
