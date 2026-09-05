package com.wikiglobal.iconconverter.hyperos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ThemeBackupManagerTest {
    @Test fun `ownership state is explicit and confirmed rebase copies current without writing active theme`() {
        val dir = Files.createTempDirectory("theme-rebase").toFile()
        val source = File(dir, "source").apply { writeBytes(byteArrayOf(1,2,3)) }
        val fake = FakeRoot(source); val store = MemoryStore(); val manager = ThemeBackupManager(dir, fake, store)
        val old = manager.ensureOriginalBackup("OS", "Launcher").getOrThrow()
        fake.replace(byteArrayOf(9,8,7))
        assertTrue(manager.ownershipState() is ThemeOwnershipState.ExternalChanged)
        val rebased = manager.rebaseToCurrentTheme("OS3", "Launcher2").getOrThrow()
        assertEquals(fake.currentSha, HyperOs3ThemePatcher.sha256(rebased.backup))
        assertTrue(File(rebased.backup.parentFile, "metadata.json").readText().contains("Launcher2"))
        assertEquals(0, fake.atomicWrites)
        assertTrue(old.backup.exists())
        assertTrue(manager.ownershipState() is ThemeOwnershipState.ManagedOriginal)
        assertEquals("OS3", rebased.hyperOsVersion)
        assertEquals("Launcher2", rebased.launcherVersion)
    }
    @Test fun `backup sha metadata and atomic install are guarded and never touch real system`() {
        val dir = Files.createTempDirectory("theme-backup").toFile(); val archive = File(dir, "source.zip").apply { writeBytes(byteArrayOf(1,2,3)) }
        val fake = FakeRoot(archive); val store = MemoryStore(); val manager = ThemeBackupManager(dir, fake, store)
        val session = manager.ensureOriginalBackup("OS3", "Launcher").getOrThrow()
        assertEquals(HyperOs3ThemePatcher.sha256(archive), HyperOs3ThemePatcher.sha256(session.backup)); assertTrue(File(session.backup.parentFile, "metadata.json").exists())
        val patched = File(dir, "patched.zip").apply { writeBytes(archive.readBytes()) }
        // Invalid archive is rejected before the fake executor receives a system install request.
        assertTrue(manager.install(patched, "pack", "ORIGINAL", null).isFailure); assertEquals(0, fake.atomicWrites)
    }
    @Test fun `restore refuses externally changed theme`() {
        val dir = Files.createTempDirectory("theme-guard").toFile(); val source = File(dir, "source").apply { writeBytes(byteArrayOf(1)) }; val fake = FakeRoot(source); val store = MemoryStore()
        val session = ThemeSession(source, fake.metadata(), lastInstalledSha256 = "managed"); store.save(session); fake.currentSha = "external"
        val result = ThemeBackupManager(dir, fake, store).restore()
        assertTrue(result.isFailure); assertTrue(result.exceptionOrNull()!!.message!!.contains("本工具之外")); assertEquals(0, fake.atomicWrites)
    }
    @Test fun `pre install external theme is refused before any atomic write`() {
        val dir = Files.createTempDirectory("theme-preinstall").toFile(); val source = File(dir, "source").apply { writeBytes(byteArrayOf(5)) }; val fake = FakeRoot(source); val store = MemoryStore()
        store.save(ThemeSession(source, fake.metadata().copy(sha256 = "original"), lastInstalledSha256 = "installed")); fake.currentSha = "external"
        val result = ThemeBackupManager(dir, fake, store).install(source, "p", "m", null)
        assertTrue(result.isFailure); assertTrue(result.exceptionOrNull()!!.message!!.contains("已停止应用")); assertEquals(0, fake.atomicWrites)
    }
    @Test fun `successful restore clears session and next apply path creates a fresh backup`() {
        val dir = Files.createTempDirectory("theme-session").toFile(); val source = File(dir, "source").apply { writeBytes(byteArrayOf(8, 9)) }; val fake = FakeRoot(source); val store = MemoryStore()
        val initial = ThemeBackupManager(dir, fake, store).ensureOriginalBackup("OS", "Launcher").getOrThrow()
        store.save(initial.copy(lastInstalledSha256 = fake.currentSha))
        ThemeBackupManager(dir, fake, store).restore().getOrThrow(); assertNull(store.load())
        val fresh = ThemeBackupManager(dir, fake, store).ensureOriginalBackup("OS", "Launcher").getOrThrow()
        assertTrue(fresh.backup.parentFile != initial.backup.parentFile)
    }
    @Test fun `CONTEXT_MISMATCH_TRIGGERS_ROLLBACK`() {
        val dir = Files.createTempDirectory("theme-context-rollback").toFile()
        val source = archive(File(dir, "source.zip"), "base")
        val patched = archive(File(dir, "patched.zip"), "patched")
        val fake = FakeRoot(source).apply { mutateNextInstall = { it.copy(selinuxContext = "u:object_r:wrong_theme:s0") } }
        val store = MemoryStore(); val manager = ThemeBackupManager(dir, fake, store)
        val original = manager.ensureOriginalBackup("OS", "Launcher").getOrThrow()
        val result = manager.install(patched, "pack", "MATERIAL", null)
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()!!.message!!
        assertTrue(error.contains("ROLLBACK_SUCCEEDED:")); assertTrue(error.contains("context=false"))
        assertTrue(error.contains("expectedContext=${original.original.selinuxContext}")); assertTrue(error.contains("actualContext=u:object_r:wrong_theme:s0"))
        assertEquals(2, fake.atomicWrites)
    }
    @Test fun `ROLLBACK_PRESERVES_ORIGINAL_METADATA`() {
        val dir = Files.createTempDirectory("theme-metadata-rollback").toFile()
        val source = archive(File(dir, "source.zip"), "base")
        val patched = archive(File(dir, "patched.zip"), "patched")
        val fake = FakeRoot(source).apply { mutateNextInstall = { it.copy(mode = "600") } }
        val manager = ThemeBackupManager(dir, fake, MemoryStore())
        val original = manager.ensureOriginalBackup("OS", "Launcher").getOrThrow()
        assertTrue(manager.install(patched, "pack", "MATERIAL", null).isFailure)
        assertEquals(original.original, fake.inspect(SuThemeRootExecutor.ACTIVE_ICONS))
    }
    private fun archive(file: File, marker: String): File = ZipOutputStream(file.outputStream()).use { out ->
        out.putNextEntry(ZipEntry("transform_config.xml")); out.write(marker.toByteArray()); out.closeEntry()
        file
    }
    private class MemoryStore : ThemeSessionStore { var value: ThemeSession? = null; override fun load() = value; override fun save(session: ThemeSession) { value = session }; override fun clear() { value = null } }
    private class FakeRoot(private val source: File) : ThemeRootExecutor {
        var currentSha = HyperOs3ThemePatcher.sha256(source); var atomicWrites = 0
        var currentMetadata = ThemeFileMetadata(currentSha, source.length(), 6101,6101,"755","u:object_r:theme_data_file:s0")
        var mutateNextInstall: ((ThemeFileMetadata) -> ThemeFileMetadata)? = null
        fun metadata() = currentMetadata.copy(sha256 = currentSha)
        override fun isRootAvailable() = true
        override fun inspect(path: String) = metadata().copy(sha256 = currentSha)
        fun replace(bytes: ByteArray) { source.writeBytes(bytes); currentSha = HyperOs3ThemePatcher.sha256(source); currentMetadata = currentMetadata.copy(sha256 = currentSha, size = source.length()) }
        override fun copySystemFileTo(source: String, destination: File) = runCatching { destination.parentFile?.mkdirs(); this.source.copyTo(destination, overwrite = true); RootOperation(true) }.getOrElse { RootOperation(false) }
        override fun atomicInstall(localArchive: File, target: String, original: ThemeFileMetadata): RootOperation {
            atomicWrites++
            currentSha = HyperOs3ThemePatcher.sha256(localArchive)
            val installed = original.copy(sha256 = currentSha, size = localArchive.length())
            currentMetadata = mutateNextInstall?.let { mutation -> mutateNextInstall = null; mutation(installed) } ?: installed
            return RootOperation(true)
        }
        override fun refreshIconCache() = RootOperation(true)
        override fun forceStopLauncher() = RootOperation(true)
    }
}
