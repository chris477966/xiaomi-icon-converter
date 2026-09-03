package com.wikiglobal.iconconverter.hyperos

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import java.util.Locale

class ThemeCompatibilityProbe(private val context: Context) {
    fun run(): ThemeCompatibilityReport {
        val shell = RootReadOnlyShell()
        if (!shell.isRootAvailable()) return ThemeCompatibilityReport(
            rootAvailable = false, systemProperties = emptyMap(), paths = emptyList(), archives = emptyList(), launcher = launcherInfo(),
            monetColors = monetColors(), adaptiveIcons = AdaptiveIconProbe(context).run(),
            notes = listOf("Root access was not granted. No system path was read.")
        )
        val detector = ThemePathDetector(shell)
        val paths = detector.detect()
        val archives = paths.mapNotNull { item ->
            detector.archiveEntries(item.path)?.let { entries ->
                ThemeArchiveInspector.inspect(item.path, entries) { entry -> detector.archiveEntry(item.path, entry) }
            }
        }
        val directoryNotes = paths.filter { shell.isDirectory(it.path) }.flatMap { item ->
            detector.directoryTree(item.path).map { "DIRECTORY_TREE ${item.path}: $it" }
        }
        return ThemeCompatibilityReport(
            rootAvailable = true,
            systemProperties = HyperOsVersionDetector(shell).detect(),
            paths = paths,
            archives = archives,
            launcher = launcherInfo(),
            monetColors = monetColors(),
            adaptiveIcons = AdaptiveIconProbe(context).run(),
            notes = directoryNotes
        )
    }

    private fun launcherInfo(): LauncherInfo? = runCatching {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo("com.miui.home", PackageManager.PackageInfoFlags.of(0))
        } else pm.getPackageInfo("com.miui.home", 0)
        LauncherInfo(info.versionName ?: "NOT_FOUND", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong(), info.applicationInfo?.sourceDir ?: "NOT_FOUND")
    }.getOrNull()

    private fun monetColors(): Map<String, String> {
        val names = listOf("system_accent1_100", "system_accent1_200", "system_accent1_700", "system_accent1_800", "system_accent2_100", "system_accent3_100")
        return names.associateWith { name ->
            val id = Resources.getSystem().getIdentifier(name, "color", "android")
            if (id == 0) "NOT_FOUND" else runCatching { String.format(Locale.US, "#%08X", Resources.getSystem().getColor(id, null)) }.getOrDefault("NOT_FOUND")
        }
    }
}
