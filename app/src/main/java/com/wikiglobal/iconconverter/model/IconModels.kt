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

data class IconPack(
    val displayName: String,
    val packageName: String,
    val mappings: List<IconMapping>,
    val calendars: List<CalendarMapping>,
    val effects: IconEffects,
    /** APK resources; intentionally kept private to app process, never copied into system directories. */
    val drawableLoader: (String) -> Drawable?
)

data class InstalledApp(
    val packageName: String,
    val launcherActivity: String,
    val label: String,
    val applicationIcon: Drawable,
    val activityIcon: Drawable? = null,
    val isSystemApp: Boolean = false,
    /** Includes aliases/targets reported by PackageManager; used for deterministic alias matching. */
    val activityAliases: Set<String> = emptySet()
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
    val dynamicCalendar: CalendarMapping? = null
)

/** Reserved persistence/UI model for a future manual override picker. */
data class ManualIconOverride(val component: ComponentKey, val drawableName: String)

data class XiaomiIconEntry(
    val packageName: String,
    val activityName: String,
    val pngBytes: ByteArray,
    val requiresActivitySpecificPath: Boolean = false
)
