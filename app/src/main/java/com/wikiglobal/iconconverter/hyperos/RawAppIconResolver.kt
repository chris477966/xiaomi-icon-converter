package com.wikiglobal.iconconverter.hyperos

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import com.wikiglobal.iconconverter.model.InstalledApp

enum class RawIconSource { ACTIVITY_RESOURCE, APPLICATION_RESOURCE, ACTIVITY_FALLBACK, APPLICATION_FALLBACK, UNAVAILABLE }
data class RawAppIcon(val drawable: Drawable?, val source: RawIconSource, val activityIconResourceId: Int, val applicationIconResourceId: Int)
data class RawIconCandidate(val drawable: Drawable?, val resourceId: Int, val source: RawIconSource)
data class RawAppIconCandidates(
    val activityResource: RawIconCandidate,
    val applicationResource: RawIconCandidate,
    val activityFallback: RawIconCandidate,
    val applicationFallback: RawIconCandidate
) {
    val inPriorityOrder get() = listOf(activityResource, applicationResource, activityFallback, applicationFallback)
}

/** Resolves the icon declared by the target APK, deliberately bypassing Launcher bitmap/cache output. */
class RawAppIconResolver(private val context: Context) {
    private val pm get() = context.packageManager
    fun resolve(app: InstalledApp): RawAppIcon = resolve(app.packageName, app.launcherActivity, app.activityIcon, app.applicationIcon)
    fun candidates(app: InstalledApp): RawAppIconCandidates = candidates(app.packageName, app.launcherActivity, app.activityIcon, app.applicationIcon)
    @Suppress("DEPRECATION")
    fun resolve(packageName: String, activityName: String, activityFallback: Drawable? = null, applicationFallback: Drawable? = null): RawAppIcon {
        val candidates = candidates(packageName, activityName, activityFallback, applicationFallback)
        val activityId = candidates.activityResource.resourceId
        val applicationId = candidates.applicationResource.resourceId
        candidates.inPriorityOrder.firstOrNull { it.drawable != null }?.let { return RawAppIcon(it.drawable, it.source, activityId, applicationId) }
        return RawAppIcon(null, RawIconSource.UNAVAILABLE, activityId, applicationId)
    }

    /** Material source discovery must inspect every declaration; it must not stop at a raster fallback. */
    @Suppress("DEPRECATION")
    fun candidates(packageName: String, activityName: String, activityFallback: Drawable? = null, applicationFallback: Drawable? = null): RawAppIconCandidates {
        val info = runCatching {
            val component = ComponentName(packageName, activityName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) pm.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0)) else pm.getActivityInfo(component, 0)
        }.getOrNull()
        val application = info?.applicationInfo
        val resources = application?.let { runCatching { pm.getResourcesForApplication(it) }.getOrNull() }
        val activityId = info?.icon ?: 0; val applicationId = application?.icon ?: 0
        fun drawable(id: Int) = if (resources != null && id != 0) runCatching { resources.getDrawable(id, null) }.getOrNull() else null
        return RawAppIconCandidates(
            RawIconCandidate(drawable(activityId), activityId, RawIconSource.ACTIVITY_RESOURCE),
            RawIconCandidate(drawable(applicationId), applicationId, RawIconSource.APPLICATION_RESOURCE),
            RawIconCandidate(activityFallback, activityId, RawIconSource.ACTIVITY_FALLBACK),
            RawIconCandidate(applicationFallback, applicationId, RawIconSource.APPLICATION_FALLBACK)
        )
    }
}
