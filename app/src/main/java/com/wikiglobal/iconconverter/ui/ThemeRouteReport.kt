package com.wikiglobal.iconconverter.ui

/** A route-only export: it intentionally contains no PNG bytes, APK data, or root details. */
object ThemeRouteReport {
    fun format(summary: ThemeApplySummary, routes: List<ThemeRouteDiagnostic>): String = buildString {
        appendLine("THEME_ROUTE_REPORT")
        appendLine("MODE=${summary.mode}")
        appendLine("GENERATED_COMPONENTS=${summary.generatedComponents}")
        appendLine("PLANNED_COMPONENTS=${summary.plannedComponents}")
        appendLine("EXISTING_ACTIVITY_ROUTE_COMPONENTS=${summary.existingActivityRouteComponents}")
        appendLine("PACKAGE_ONLY_COMPONENTS=${summary.packageOnlyComponents}")
        appendLine("LEGACY_ALIAS_COMPONENTS=${summary.legacyAliasComponents}")
        appendLine("PACKAGE_BASE_ENTRIES=${summary.packageBaseEntries}")
        appendLine("ACTIVITY_ALIAS_ENTRIES=${summary.activityAliasEntries}")
        appendLine("TOTAL_REPLACEMENTS=${summary.totalReplacements}")
        appendLine("ACTIVITY_ENTRIES_MATCHED=${summary.existingActivityEntriesMatched}")
        appendLine("UNROUTED_COMPONENTS=${summary.unroutedComponents}")
        appendLine("ENTRY_CONFLICTS=${summary.entryConflicts}")
        appendLine("PATCH_VERIFIED=${summary.patchVerified}")
        appendLine("ARCHIVE_INSTALL_VERIFIED=${summary.archiveInstallVerified}")
        appendLine("LAUNCHER_REFRESH_STATUS=${summary.launcherRefreshStatus}")
        routes.forEach { route ->
            appendLine()
            appendLine("PACKAGE=${route.packageName}")
            appendLine("LAUNCHER_ACTIVITY=${route.launcherActivity}")
            appendLine("TARGET_ACTIVITY=${route.targetActivity ?: "NONE"}")
            appendLine("CURRENT_ACTIVITY_ENTRY=${route.currentActivityEntry}")
            appendLine("CURRENT_ACTIVITY_ENTRY_EXISTED=${route.currentActivityEntryExisted}")
            appendLine("DIRECT_MATCHED_ENTRIES=${route.directMatchedEntries.joinToString(",")}")
            appendLine("TARGET_FALLBACK_MATCHED_ENTRIES=${route.targetFallbackMatchedEntries.joinToString(",")}")
            appendLine("LEGACY_THEME_ALIAS_ENTRIES=${route.legacyThemeAliasEntries.joinToString(",")}")
            appendLine("LEGACY_ALIAS_STATUS=${route.legacyAliasStatus}")
            appendLine("FINAL_REPLACEMENT_ENTRIES=${route.finalReplacementEntries.joinToString(",")}")
        }
    }
}
