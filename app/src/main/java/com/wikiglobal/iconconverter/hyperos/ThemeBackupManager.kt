package com.wikiglobal.iconconverter.hyperos

import java.io.File
import java.time.Instant

data class ThemeSession(
    val backup: File,
    val original: ThemeFileMetadata,
    val lastInstalledSha256: String? = null,
    val paletteHash: String? = null,
    val hyperOsVersion: String = "UNKNOWN",
    val launcherVersion: String = "UNKNOWN"
)
sealed class ThemeOwnershipState {
    data object Unmanaged : ThemeOwnershipState()
    data object ManagedOriginal : ThemeOwnershipState()
    data object ManagedInstalled : ThemeOwnershipState()
    data object ExternalChanged : ThemeOwnershipState()
}
interface ThemeSessionStore { fun load(): ThemeSession?; fun save(session: ThemeSession); fun clear() }
class FileThemeSessionStore(private val file: File) : ThemeSessionStore {
    override fun load(): ThemeSession? = runCatching {
        val values = file.readLines().mapNotNull { line -> line.substringBefore('=').takeIf { '=' in line }?.let { it to line.substringAfter('=') } }.toMap()
        val backup = File(values.getValue("backup")); val original = ThemeFileMetadata(values.getValue("sha"), values.getValue("size").toLong(), values.getValue("uid").toInt(), values.getValue("gid").toInt(), values.getValue("mode"), values.getValue("context"))
        ThemeSession(backup, original, values["installed"].orEmpty().ifBlank { null }, values["palette"].orEmpty().ifBlank { null }, values["hyperOsVersion"] ?: "UNKNOWN", values["launcherVersion"] ?: "UNKNOWN")
    }.getOrNull()
    override fun save(session: ThemeSession) { file.parentFile?.mkdirs(); file.writeText("backup=${session.backup.absolutePath}\nsha=${session.original.sha256}\nsize=${session.original.size}\nuid=${session.original.uid}\ngid=${session.original.gid}\nmode=${session.original.mode}\ncontext=${session.original.selinuxContext}\ninstalled=${session.lastInstalledSha256.orEmpty()}\npalette=${session.paletteHash.orEmpty()}\nhyperOsVersion=${session.hyperOsVersion}\nlauncherVersion=${session.launcherVersion}\n") }
    override fun clear() { if (file.exists()) file.delete() }
}

class ThemeBackupManager(private val filesDir: File, private val root: ThemeRootExecutor, private val store: ThemeSessionStore) {
    fun currentSession(): ThemeSession? = store.load()
    fun ownershipState(): ThemeOwnershipState {
        val session = store.load() ?: return ThemeOwnershipState.Unmanaged
        val current = root.inspect(SuThemeRootExecutor.ACTIVE_ICONS) ?: return ThemeOwnershipState.ExternalChanged
        return when (current.sha256) {
            session.original.sha256 -> ThemeOwnershipState.ManagedOriginal
            session.lastInstalledSha256 -> ThemeOwnershipState.ManagedInstalled
            else -> ThemeOwnershipState.ExternalChanged
        }
    }

    fun ensureOriginalBackup(hyperOsVersion: String, launcherVersion: String): Result<ThemeSession> = runCatching {
        store.load()?.let { return@runCatching it }
        val metadata = root.inspect(SuThemeRootExecutor.ACTIVE_ICONS) ?: error("Cannot read active theme icons")
        val directory = File(filesDir, "backups/${Instant.now().toEpochMilli()}-${System.nanoTime()}").also { it.mkdirs() }; val backup = File(directory, "icons")
        check(root.copySystemFileTo(SuThemeRootExecutor.ACTIVE_ICONS, backup).success) { "Theme backup copy failed" }
        check(HyperOs3ThemePatcher.sha256(backup) == metadata.sha256) { "Backup SHA mismatch" }
        File(directory, "metadata.json").writeText("{\n  \"sha256\": \"${metadata.sha256}\",\n  \"size\": ${metadata.size},\n  \"uid\": ${metadata.uid},\n  \"gid\": ${metadata.gid},\n  \"mode\": \"${metadata.mode}\",\n  \"selinuxContext\": \"${metadata.selinuxContext}\",\n  \"timestamp\": \"${Instant.now()}\",\n  \"hyperOsVersion\": \"$hyperOsVersion\",\n  \"launcherVersion\": \"$launcherVersion\"\n}\n")
        ThemeSession(backup, metadata, hyperOsVersion = hyperOsVersion, launcherVersion = launcherVersion).also(store::save)
    }

    /** Explicit foreground-only baseline transfer. It never installs or changes the active theme. */
    fun rebaseToCurrentTheme(hyperOsVersion: String, launcherVersion: String): Result<ThemeSession> = runCatching {
        val current = root.inspect(SuThemeRootExecutor.ACTIVE_ICONS) ?: error("Cannot read active theme icons")
        val directory = File(filesDir, "backups/${Instant.now().toEpochMilli()}-${System.nanoTime()}").also { it.mkdirs() }
        val backup = File(directory, "icons")
        check(root.copySystemFileTo(SuThemeRootExecutor.ACTIVE_ICONS, backup).success) { "Theme rebase copy failed" }
        check(HyperOs3ThemePatcher.sha256(backup) == current.sha256) { "Rebase SHA mismatch" }
        File(directory, "metadata.json").writeText("{\n  \"sha256\": \"${current.sha256}\",\n  \"size\": ${current.size},\n  \"uid\": ${current.uid},\n  \"gid\": ${current.gid},\n  \"mode\": \"${current.mode}\",\n  \"selinuxContext\": \"${current.selinuxContext}\",\n  \"timestamp\": \"${Instant.now()}\",\n  \"hyperOsVersion\": \"$hyperOsVersion\",\n  \"launcherVersion\": \"$launcherVersion\"\n}\n")
        ThemeSession(backup, current, lastInstalledSha256 = null, paletteHash = null, hyperOsVersion = hyperOsVersion, launcherVersion = launcherVersion).also(store::save)
    }
    fun install(patched: File, iconPack: String, mode: String, paletteHash: String?, hyperOsVersion: String = "UNKNOWN", launcherVersion: String = "UNKNOWN"): Result<ThemeSession> = runCatching {
        val current = root.inspect(SuThemeRootExecutor.ACTIVE_ICONS) ?: error("Cannot inspect current theme")
        val existing = store.load()
        if (existing != null) check(current.sha256 == existing.original.sha256 || current.sha256 == existing.lastInstalledSha256) {
            "当前系统主题已在本工具之外发生变化。为避免覆盖新主题，已停止应用。"
        }
        val session = existing ?: ensureOriginalBackup(hyperOsVersion, launcherVersion).getOrThrow()
        check(HyperOs3ThemePatcher.validatePatchedArchive(patched, emptySet())) { "Patched archive is invalid" }
        val patchedSha = HyperOs3ThemePatcher.sha256(patched)
        val result = root.atomicInstall(patched, SuThemeRootExecutor.ACTIVE_ICONS, session.original); check(result.success) { result.message }
        val installed = root.inspect(SuThemeRootExecutor.ACTIVE_ICONS)
        val metadataMatches = installed != null && installed.sha256 == patchedSha && installed.uid == session.original.uid && installed.gid == session.original.gid && installed.mode == session.original.mode && installed.selinuxContext == session.original.selinuxContext
        if (!metadataMatches) {
            val rollback = root.atomicInstall(session.backup, SuThemeRootExecutor.ACTIVE_ICONS, session.original)
            val restored = root.inspect(SuThemeRootExecutor.ACTIVE_ICONS)
            check(rollback.success && restored?.sha256 == session.original.sha256) { "ROLLBACK_FAILED" }
            error("ROLLBACK_SUCCEEDED")
        }
        session.copy(lastInstalledSha256 = patchedSha, paletteHash = paletteHash).also(store::save)
    }
    fun restore(): Result<Unit> = runCatching {
        val session = store.load() ?: error("No managed backup")
        val current = root.inspect(SuThemeRootExecutor.ACTIVE_ICONS) ?: error("Cannot inspect current theme")
        check(current.sha256 == session.lastInstalledSha256) { "系统图标主题已在本工具之外发生变化。为避免覆盖当前主题，自动恢复已停止。" }
        check(root.atomicInstall(session.backup, SuThemeRootExecutor.ACTIVE_ICONS, session.original).success) { "Atomic restore failed" }
        val restored = root.inspect(SuThemeRootExecutor.ACTIVE_ICONS)
        check(restored?.sha256 == session.original.sha256 && restored.uid == session.original.uid && restored.gid == session.original.gid && restored.mode == session.original.mode && restored.selinuxContext == session.original.selinuxContext) { "Restore verification failed" }
        store.clear()
    }
}
