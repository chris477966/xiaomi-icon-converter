package com.wikiglobal.iconconverter.hyperos

import java.io.File
import java.util.zip.ZipFile

data class RenderedThemeActivityIcon(
    val packageName: String,
    val launcherActivity: String,
    val equivalentActivities: Set<String> = emptySet(),
    val targetActivity: String? = null,
    val png: ByteArray
) {
    constructor(packageName: String, launcherActivity: String, equivalentActivities: Set<String>, png: ByteArray) : this(packageName, launcherActivity, equivalentActivities, null, png)
    val identity get() = LauncherComponentIdentity.from(packageName, launcherActivity, targetActivity, equivalentActivities)
}
data class ThemeEntryConflict(val entryName: String, val firstPackage: String, val firstActivity: String, val secondPackage: String, val secondActivity: String)
data class ThemeApplicationPlan(
    val replacements: List<HyperOs3IconReplacement>,
    val components: List<ComponentThemeRoute>,
    val unroutedComponents: List<LauncherComponentIdentity> = emptyList(),
    val entryConflicts: List<ThemeEntryConflict> = emptyList()
) {
    val generatedComponentCount get() = components.size + unroutedComponents.size
    val plannedComponentCount get() = components.size
    val existingActivityRouteComponents get() = components.count { it.matchedActivityEntries.isNotEmpty() }
    val legacyAliasComponents get() = components.count { it.legacyAliasStatus == LegacyAliasStatus.UNIQUE }
    val packageOnlyComponents get() = generatedComponentCount - existingActivityRouteComponents
    val existingActivityEntriesMatched get() = components.sumOf { it.matchedActivityEntries.size }
    val packageBaseReplacementCount get() = replacements.count { it.entryName.substringAfterLast('/').substringBeforeLast('.') == it.packageName }
    val activityAliasReplacementCount get() = replacements.size - packageBaseReplacementCount
    val totalReplacementCount get() = replacements.size
}

/** Shared icon-pack and Material route planner. Base entry ownership is stable by full launcher activity order. */
object HyperOsThemeReplacementPlanner {
    fun plan(base: File, rendered: List<RenderedThemeActivityIcon>): ThemeApplicationPlan = plan(HyperOsThemeArchiveIndex.from(base), rendered)
    fun plan(index: HyperOsThemeArchiveIndex, rendered: List<RenderedThemeActivityIcon>): ThemeApplicationPlan {
        val ordered = rendered.sortedWith(compareBy<RenderedThemeActivityIcon> { it.packageName }.thenBy { it.identity.launcherActivity })
        val routes = ordered.map { icon -> icon to HyperOsThemeEntryResolver.resolve(index, icon.identity) }
        val selected = linkedMapOf<String, Pair<RenderedThemeActivityIcon, ByteArray>>()
        val conflicts = mutableListOf<ThemeEntryConflict>(); val baseOwners = mutableSetOf<String>()
        fun claim(entry:String, icon:RenderedThemeActivityIcon, allowDirectOwner:Boolean) {
            val previous=selected[entry]
            if(previous==null) selected[entry]=icon to icon.png
            else if(!previous.second.contentEquals(icon.png) && allowDirectOwner) conflicts+=ThemeEntryConflict(entry,previous.first.packageName,previous.first.identity.launcherActivity,icon.packageName,icon.identity.launcherActivity)
        }
        routes.forEach { (icon,route) -> if(baseOwners.add(route.packageEntry)) selected[route.packageEntry]=icon to icon.png }
        // Direct entries establish ownership before any target fallback may claim them.
        routes.forEach { (icon,route) -> route.directMatchedEntries.forEach { claim(it,icon,true) } }
        routes.forEach { (icon,route) -> route.targetFallbackMatchedEntries.forEach { entry ->
            // An existing direct claim wins; only competing target fallbacks remain conflicts.
            val directOwner=routes.any { (_,candidate)->entry in candidate.directMatchedEntries }
            if(!directOwner) claim(entry,icon,true)
        } }
        // A unique legacy archive alias is last-resort data-driven compatibility only.
        // It never competes with an exact direct or target route already established above.
        routes.forEach { (icon,route) -> route.legacyThemeAliasEntries.forEach { entry ->
            val higherOwner=routes.any { (_,candidate) -> entry in candidate.directMatchedEntries || entry in candidate.targetFallbackMatchedEntries }
            if(!higherOwner) claim(entry,icon,true)
        } }
        return ThemeApplicationPlan(
            replacements = selected.map { (entry, value) -> HyperOs3IconReplacement(value.first.packageName, value.second, exactEntryName = entry) },
            components = routes.map { it.second }, entryConflicts = conflicts.distinct()
        )
    }
}

/** Verifies exact planned bytes in the local patched archive before any root install. */
object ThemeApplicationPlanVerifier {
    fun verify(patchedArchive: File, plan: ThemeApplicationPlan): Boolean = runCatching {
        check(plan.entryConflicts.isEmpty())
        ZipFile(patchedArchive).use { zip -> plan.replacements.all { replacement ->
            val entry = zip.getEntry(replacement.entryName) ?: return@all false
            HyperOs3ThemePatcher.sha256(zip.getInputStream(entry).readBytes()) == HyperOs3ThemePatcher.sha256(replacement.png)
        } }
    }.getOrDefault(false)
}
