package com.wikiglobal.iconconverter.hyperos

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.VectorDrawable
import android.os.Build

/** Inspects icon resources from each target APK, never from the Launcher-rendered bitmap/cache. */
class AdaptiveIconProbe(private val context: Context) {
    fun run(): AdaptiveIconReport {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else pm.queryIntentActivities(intent, 0)
        val samples = resolved.mapNotNull { resolve ->
            val activity = resolve.activityInfo ?: return@mapNotNull null
            val component = ComponentName(activity.packageName, activity.name)
            val activityLookup = runCatching { pm.getActivityIcon(component) }.getOrNull()
            val loaded = runCatching { activity.loadIcon(pm) }.getOrNull()
            val resources = runCatching { pm.getResourcesForApplication(activity.applicationInfo) }.getOrNull()
            val resourceId = activity.icon.takeIf { it != 0 } ?: activity.applicationInfo.icon.takeIf { it != 0 } ?: 0
            val raw = if (resources != null && resourceId != 0) runCatching { resources.getDrawable(resourceId, null) }.getOrNull() else null
            val inspected = raw ?: loaded ?: activityLookup
            val adaptive = inspected as? AdaptiveIconDrawable
            val monochrome = if (adaptive != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (adaptive.monochrome == null) "NO" else "YES"
            } else if (adaptive != null) "API_UNAVAILABLE" else "NOT_APPLICABLE"
            AdaptiveIconSample(
                packageName = activity.packageName,
                activityName = activity.name,
                activityIconId = activity.icon,
                applicationIconId = activity.applicationInfo.icon,
                getActivityIconClass = activityLookup.className(),
                loadIconClass = loaded.className(),
                rawIconClass = raw.className(),
                category = categoryOf(inspected),
                foregroundClass = adaptive?.foreground?.javaClass?.simpleName,
                backgroundClass = adaptive?.background?.javaClass?.simpleName,
                monochrome = monochrome
            )
        }.distinctBy { it.packageName to it.activityName }
        return AdaptiveIconReport(
            launcherActivities = samples.size,
            adaptiveNative = samples.count { it.category == "ADAPTIVE_NATIVE" },
            nativeMonochrome = samples.count { it.monochrome == "YES" },
            noMonochrome = samples.count { it.monochrome == "NO" },
            bitmapLegacy = samples.count { it.category == "BITMAP" },
            vector = samples.count { it.category == "VECTOR" },
            other = samples.count { it.category == "OTHER" },
            samples = samples.take(10)
        )
    }

    private fun Drawable?.className() = this?.javaClass?.simpleName ?: "NOT_FOUND"
    private fun categoryOf(drawable: Drawable?): String = when (drawable) {
        is AdaptiveIconDrawable -> "ADAPTIVE_NATIVE"
        is BitmapDrawable -> "BITMAP"
        is VectorDrawable -> "VECTOR"
        null -> "OTHER"
        else -> "OTHER"
    }
}
