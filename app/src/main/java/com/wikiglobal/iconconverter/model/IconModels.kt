package com.wikiglobal.iconconverter.model

import android.graphics.drawable.Drawable

/** A normalized launcher component. activity is null only for package-level appfilter entries. */
data class ComponentKey(val packageName: String, val activityName: String? = null) {
    companion object {
        fun normalized(packageName: String, activityName: String?): ComponentKey = ComponentKey(
            packageName.trim(),
            activityName?.trim()?.takeIf { it.isNotEmpty() }?.let {
                if (it.startsWith(".")) packageName + it else it
            }
        )
    }
}

data class IconMapping(val component: ComponentKey, val drawableName: String)
data class CalendarMapping(val component: ComponentKey, val prefix: String, val dayDrawables: Map<Int, String>)
data class IconEffects(val iconBack: List<String> = emptyList(), val iconMask: List<String> = emptyList(), val iconUpon: List<String> = emptyList(), val scale: Float? = null)

enum class IconAssignmentType { AUTOMATIC, MANUAL }

/** A resource in an icon pack. The resource name is the stable lookup key; previews are lazy. */
data class IconEntry(
    val iconPackId: String,
    val resourceName: String,
    val resourceIdentifier: Int,
    val resourceType: String = "drawable",
    val mappedPackageNames: Set<String> = emptySet(),
    val searchableKeywords: Set<String> = emptySet()
)

data class AppIconAssignment(
    val appIdentifier: String,
    val iconPackId: String,
    val resourceName: String,
    val assignmentType: IconAssignmentType,
    val sourceAvailable: Boolean = true,
    val resourceIdentifier: Int = 0
)

data class IconPack(
    val displayName: String,
    val packageName: String,
    val mappings: List<IconMapping>,
    val calendars: List<CalendarMapping>,
    val effects: IconEffects,
    /** APK resources; intentionally kept private to app process, never copied into system directories. */
    val drawableLoader: (String) -> Drawable?,
    val id: String = packageName,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val entries: List<IconEntry> = emptyList(),
    val resourceLoader: ((Int) -> Drawable?)? = null
)

data class InstalledApp(
    val packageName: String,
    val launcherActivity: String,
    val label: String,
    val applicationIcon: Drawable,
    val activityIcon: Drawable? = null,
    /** Raw APK resource ids are retained so Monet can bypass Launcher-rasterized icons. */
    val activityIconResourceId: Int = 0,
    val applicationIconResourceId: Int = 0,
    val isSystemApp: Boolean = false,
    /** Includes aliases/targets reported by PackageManager; used for deterministic alias matching. */
    val activityAliases: Set<String> = emptySet(),
    /** Resolved ActivityInfo.targetActivity; routing treats it as lower-priority fallback. */
    val targetActivity: String? = null
) {
    /** Activity icon gives a launcher alias its actual presentation; application icon remains available for export/UI. */
    val originalIcon: Drawable get() = activityIcon ?: applicationIcon
}

enum class MatchStatus { EXACT_COMPONENT, PACKAGE, ALIAS, CONFLICT, UNMATCHED }
enum class MatchConfidence { HIGH, MEDIUM, LOW, NONE }

data class IconMatch(
    val app: InstalledApp,
    val status: MatchStatus,
    val confidence: MatchConfidence,
    val drawableName: String? = null,
    val detail: String = "",
    val dynamicCalendar: CalendarMapping? = null,
    val assignmentType: IconAssignmentType = IconAssignmentType.AUTOMATIC,
    val sourceIconPackId: String? = null,
    val resourceIdentifier: Int = 0
)

/** Reserved persistence/UI model for a future manual override picker. */
data class ManualIconOverride(val component: ComponentKey, val drawableName: String)

data class XiaomiIconEntry(
    val packageName: String,
    val activityName: String,
    val pngBytes: ByteArray,
    val requiresActivitySpecificPath: Boolean = false
)
