package com.wikiglobal.iconconverter.hyperos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ThemeBackupManagerTest {
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
    private class MemoryStore : ThemeSessionStore { var value: ThemeSession? = null; override fun load() = value; override fun save(session: ThemeSession) { value = session } }
    private class FakeRoot(private val source: File) : ThemeRootExecutor {
        var currentSha = HyperOs3ThemePatcher.sha256(source); var atomicWrites = 0
        fun metadata() = ThemeFileMetadata(currentSha, source.length(), 6101,6101,"755","u:object_r:theme_data_file:s0")
        override fun isRootAvailable() = true
        override fun inspect(path: String) = metadata().copy(sha256 = currentSha)
        override fun copySystemFileTo(source: String, destination: File) = runCatching { destination.parentFile?.mkdirs(); this.source.copyTo(destination, overwrite = true); RootOperation(true) }.getOrElse { RootOperation(false) }
        override fun atomicInstall(localArchive: File, target: String, original: ThemeFileMetadata): RootOperation { atomicWrites++; currentSha = HyperOs3ThemePatcher.sha256(localArchive); return RootOperation(true) }
    }
}
