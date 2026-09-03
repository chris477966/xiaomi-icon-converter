package com.wikiglobal.iconconverter.hyperos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeArchiveInspectorTest {
    @Test fun `reports actual layered Xiaomi-style archive signals without assuming them`() {
        val entries = listOf(
            "transform_config.xml",
            "res/drawable-xxhdpi/com.example/0.png",
            "res/drawable-xxhdpi/com.example/1.png",
            "res/drawable-xxhdpi/com.example.png",
            "fancy_icons/com.example/manifest.xml",
            "fancy_icons/com.example/icon.png",
            "dynamicicons/com.example/day_1.png",
            "layer_animating_icons/com.example/config.xml"
        )
        val report = ThemeArchiveInspector.inspect("/data/system/theme/icons", entries) { entry ->
            when (entry) {
                "transform_config.xml" -> "<transform scale=\"1\"><item/></transform>".toByteArray()
                "fancy_icons/com.example/manifest.xml" -> "<manifest><icon name=\"x\"/></manifest>".toByteArray()
                else -> null
            }
        }
        assertTrue(report.isZipCompatible)
        assertTrue(report.flags.hasTransformConfig)
        assertTrue(report.flags.hasDrawableXxhdpi)
        assertTrue(report.flags.hasPackageDirectories)
        assertTrue(report.flags.hasPackagePng)
        assertTrue(report.flags.hasLayer0Png)
        assertTrue(report.flags.hasLayer1Png)
        assertTrue(report.flags.hasFancyIcons)
        assertTrue(report.flags.hasFancyManifest)
        assertTrue(report.flags.hasDynamicIcons)
        assertTrue(report.flags.hasLayerAnimatingIcons)
        assertTrue(report.transformConfigSummary.contains("root=transform"))
        assertEquals(listOf("res/drawable-xxhdpi/com.example/0.png", "res/drawable-xxhdpi/com.example/1.png"), report.packageDirectoryTrees.getValue("res/drawable-xxhdpi/com.example"))
    }

    @Test fun `marks absent structures false`() {
        val report = ThemeArchiveInspector.inspect("icons", listOf("res/drawable-xxhdpi/com.example.png")) { null }
        assertTrue(report.flags.hasDrawableXxhdpi)
        assertTrue(report.flags.hasPackagePng)
        assertTrue(!report.flags.hasLayer0Png)
        assertTrue(!report.flags.hasFancyIcons)
        assertEquals("NOT_FOUND", report.transformConfigSummary)
    }

    @Test fun `parses unzip long listing fallback`() {
        val listing = """
            Archive: icons
              Length      Date    Time    Name
            ---------  ---------- -----   ----
                    4  2026-09-03 02:00   res/drawable-xxhdpi/com.example.png
            ---------                     -------
        """.trimIndent()
        assertEquals(listOf("res/drawable-xxhdpi/com.example.png"), ThemePathDetector.parseLongZipListing(listing))
    }
}
