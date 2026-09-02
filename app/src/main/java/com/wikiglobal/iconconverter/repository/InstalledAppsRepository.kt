package com.wikiglobal.iconconverter.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.wikiglobal.iconconverter.model.InstalledApp

class InstalledAppsRepository(private val context: Context) {
    private val packageManager: PackageManager get() = context.packageManager

    /** Returns one entry for each exported launcher activity; non-launcher packages never appear. */
    fun launcherApps(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        return packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { resolved ->
                val info = resolved.activityInfo ?: return@mapNotNull null
                val aliases = setOfNotNull(info.name, info.targetActivity)
                InstalledApp(
                    packageName = info.packageName,
                    launcherActivity = normalize(info.packageName, info.name),
                    label = resolved.loadLabel(packageManager).toString(),
                    originalIcon = resolved.loadIcon(packageManager),
                    activityAliases = aliases.map { normalize(info.packageName, it) }.toSet()
                )
            }
            .distinctBy { it.packageName to it.launcherActivity }
            .sortedBy { it.label.lowercase() }
    }

    private fun normalize(pkg: String, activity: String) = if (activity.startsWith('.')) pkg + activity else activity
}
