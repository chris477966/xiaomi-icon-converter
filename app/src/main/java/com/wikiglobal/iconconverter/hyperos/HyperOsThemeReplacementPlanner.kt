package com.wikiglobal.iconconverter.hyperos

import java.io.File
import java.util.zip.ZipFile

data class RenderedThemeActivityIcon(
    val packageName: String,
    val launcherActivity: String,
    val equivalentActivities: Set<String> = emptySet(),
    val png: ByteArray
) {
    val identity get() = LauncherComponentIdentity.from(packageName, launcherActivity, equivalentActivities)
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
        routes.forEach { (icon, route) -> route.replacementEntries.forEach { entry ->
            if (entry == route.packageEntry && !baseOwners.add(entry)) return@forEach
            val previous = selected[entry]
            if (previous == null) selected[entry] = icon to icon.png
            else if (!previous.second.contentEquals(icon.png)) conflicts += ThemeEntryConflict(entry, previous.first.packageName, previous.first.identity.launcherActivity, icon.packageName, icon.identity.launcherActivity)
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
