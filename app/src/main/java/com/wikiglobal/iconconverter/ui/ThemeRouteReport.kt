package com.wikiglobal.iconconverter.ui

/** A route-only export: it intentionally contains no PNG bytes, APK data, or root details. */
object ThemeRouteReport {
    fun format(summary: ThemeApplySummary, routes: List<ThemeRouteDiagnostic>): String = buildString {
        appendLine("THEME_ROUTE_REPORT")
        appendLine("MODE=${summary.mode}")
        appendLine("GENERATED_COMPONENTS=${summary.generatedComponents}")
        appendLine("PLANNED_COMPONENTS=${summary.plannedComponents}")
        routes.forEach { route ->
            appendLine()
            appendLine("PACKAGE=${route.packageName}")
            appendLine("LAUNCHER_ACTIVITY=${route.launcherActivity}")
            appendLine("TARGET_ACTIVITY=${route.targetActivity ?: "NONE"}")
            appendLine("DIRECT_MATCHED_ENTRIES=${route.directMatchedEntries.joinToString(",")}")
            appendLine("TARGET_FALLBACK_MATCHED_ENTRIES=${route.targetFallbackMatchedEntries.joinToString(",")}")
            appendLine("LEGACY_THEME_ALIAS_ENTRIES=${route.legacyThemeAliasEntries.joinToString(",")}")
            appendLine("LEGACY_ALIAS_STATUS=${route.legacyAliasStatus}")
            appendLine("FINAL_REPLACEMENT_ENTRIES=${route.finalReplacementEntries.joinToString(",")}")
        }
    }
}
