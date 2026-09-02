package com.wikiglobal.iconconverter.repository

import android.graphics.drawable.ColorDrawable
import com.wikiglobal.iconconverter.model.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class InstalledAppsRepositoryTest {
    private fun launcher(packageName: String, activity: String, label: String = "App") = InstalledApp(
        packageName = packageName,
        launcherActivity = activity,
        label = label,
        applicationIcon = ColorDrawable(),
        activityIcon = ColorDrawable(),
        isSystemApp = false
    )

    @Test fun `keeps multiple launcher activities from the same package`() {
        val first = launcher("com.example", "com.example.First")
        val second = launcher("com.example", "com.example.Second")
        val result = InstalledAppsRepository.deduplicateLauncherActivities(listOf(first, second))
        assertEquals(listOf("com.example.First", "com.example.Second"), result.map { it.launcherActivity })
    }

    @Test fun `removes only exact duplicate package activity`() {
        val first = launcher("com.example", "com.example.First", "First")
        val duplicate = launcher("com.example", "com.example.First", "Duplicate label")
        val result = InstalledAppsRepository.deduplicateLauncherActivities(listOf(first, duplicate))
        assertEquals(1, result.size)
        assertSame(first, result.single())
    }

    @Test fun `preserves package and activity independently`() {
        val app = launcher("com.example", "com.example.Launcher")
        assertEquals("com.example", app.packageName)
        assertEquals("com.example.Launcher", app.launcherActivity)
        assertSame(app.activityIcon, app.originalIcon)
    }
}
