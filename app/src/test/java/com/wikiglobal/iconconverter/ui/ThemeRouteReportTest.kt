package com.wikiglobal.iconconverter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeRouteReportTest {
    private val summary = ThemeApplySummary("MATERIAL_YOU", 1, 1, 1, 0, 2, 1, 1, 2, 3, 0, 0, true, true, LauncherRefreshStatus.SUCCESS)
    private val route = ThemeRouteDiagnostic(
        packageName = "com.miui.gallery",
        launcherActivity = "com.miui.gallery.activity.HomePageActivity",
        targetActivity = "com.miui.gallery.activity.TargetActivity",
        directMatchedEntries = listOf("res/drawable-xxhdpi/com.miui.gallery.activity.HomePageActivity.png"),
        targetFallbackMatchedEntries = listOf("res/drawable-xxhdpi/com.miui.gallery.activity.TargetActivity.png"),
        legacyThemeAliasEntries = listOf("res/drawable-xxhdpi/com.miui.gallery.activity.LegacyHome.png"),
        legacyAliasStatus = "UNIQUE",
        finalReplacementEntries = listOf("res/drawable-xxhdpi/com.miui.gallery.png")
    )

    @Test fun LAST_APPLY_ROUTES_CAPTURED() {
        val state = ConverterUiState(lastApplyRoutes = listOf(route))
        assertEquals(listOf(route), state.lastApplyRoutes)
    }

    @Test fun ROUTE_REPORT_CONTAINS_DIRECT_ENTRIES() = assertTrue(ThemeRouteReport.format(summary, listOf(route)).contains("DIRECT_MATCHED_ENTRIES=res/drawable-xxhdpi/com.miui.gallery.activity.HomePageActivity.png"))

    @Test fun ROUTE_REPORT_CONTAINS_TARGET_FALLBACK() = assertTrue(ThemeRouteReport.format(summary, listOf(route)).contains("TARGET_FALLBACK_MATCHED_ENTRIES=res/drawable-xxhdpi/com.miui.gallery.activity.TargetActivity.png"))

    @Test fun ROUTE_REPORT_CONTAINS_FINAL_ENTRIES() = assertTrue(ThemeRouteReport.format(summary, listOf(route)).contains("FINAL_REPLACEMENT_ENTRIES=res/drawable-xxhdpi/com.miui.gallery.png"))

    @Test fun LEGACY_ALIAS_DIAGNOSTIC_EXPORTED() {
        val report = ThemeRouteReport.format(summary, listOf(route))
        assertTrue(report.contains("LEGACY_THEME_ALIAS_ENTRIES=res/drawable-xxhdpi/com.miui.gallery.activity.LegacyHome.png"))
        assertTrue(report.contains("LEGACY_ALIAS_STATUS=UNIQUE"))
    }

    @Test fun ROUTE_REPORT_NO_PNG_BYTES() {
        val report = ThemeRouteReport.format(summary, listOf(route))
        assertFalse(report.contains("ByteArray"))
        assertFalse(report.contains("PNG_BYTES"))
    }

    @Test fun FAILED_APPLY_PRESERVES_LAST_SUCCESS_DIAGNOSTIC() {
        val afterFailure = ConverterUiState(lastApplyRoutes = listOf(route)).copy(themeOperationRunning = false, message = "应用失败")
        assertEquals(listOf(route), afterFailure.lastApplyRoutes)
    }
}
