package com.wikiglobal.iconconverter.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import com.wikiglobal.iconconverter.model.InstalledApp

class InstalledAppsRepository(private val context: Context) {
    private val packageManager: PackageManager get() = context.packageManager

    /** Returns one entry per unique package/activity pair, including both user and system launcher activities. */
    fun launcherApps(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return deduplicateLauncherActivities(queryLauncherActivities(intent)
            .mapNotNull { resolved ->
                val info = resolved.activityInfo ?: return@mapNotNull null
                val aliases = setOfNotNull(info.name, info.targetActivity)
                InstalledApp(
                    packageName = info.packageName,
                    launcherActivity = normalize(info.packageName, info.name),
                    label = resolved.loadLabel(packageManager).toString(),
                    applicationIcon = info.applicationInfo.loadIcon(packageManager),
                    activityIcon = runCatching { info.loadIcon(packageManager) }.getOrNull(),
                    activityIconResourceId = info.icon,
                    applicationIconResourceId = info.applicationInfo.icon,
                    isSystemApp = info.applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                    activityAliases = aliases.map { normalize(info.packageName, it) }.toSet()
                )
            }
        ).sortedWith(compareBy<InstalledApp> { it.label.lowercase() }.thenBy { it.packageName }.thenBy { it.launcherActivity })
    }

    @Suppress("DEPRECATION")
    private fun queryLauncherActivities(intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            packageManager.queryIntentActivities(intent, 0)
        }

    private fun normalize(pkg: String, activity: String) = when {
        activity.startsWith('.') -> pkg + activity
        activity.startsWith("$pkg.") || activity == pkg -> activity
        else -> "$pkg.$activity"
    }

    companion object {
        /** Kept public and pure so duplicates and multi-activity behavior can be tested without PackageManager. */
        fun deduplicateLauncherActivities(apps: List<InstalledApp>): List<InstalledApp> =
            apps.distinctBy { it.packageName to it.launcherActivity }
    }
}
