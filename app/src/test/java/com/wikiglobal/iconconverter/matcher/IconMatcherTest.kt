package com.wikiglobal.iconconverter.matcher

import android.graphics.drawable.ColorDrawable
import com.wikiglobal.iconconverter.model.ComponentKey
import com.wikiglobal.iconconverter.model.IconMapping
import com.wikiglobal.iconconverter.model.InstalledApp
import com.wikiglobal.iconconverter.model.MatchStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class IconMatcherTest {
    private val app = InstalledApp("com.demo", "com.demo.Home", "Demo", ColorDrawable(), setOf("com.demo.Home"))
    @Test fun `uses package fallback when activity differs`() {
        val match = IconMatcher.matchOne(app, listOf(IconMapping(ComponentKey("com.demo", "com.demo.Other"), "fallback")))
        assertEquals(MatchStatus.PACKAGE, match.status)
        assertEquals("fallback", match.drawableName)
    }
    @Test fun `does not randomly select duplicate package drawable conflict`() {
        val match = IconMatcher.matchOne(app, listOf(
            IconMapping(ComponentKey("com.demo", "com.demo.One"), "one"),
            IconMapping(ComponentKey("com.demo", "com.demo.Two"), "two")
        ))
        assertEquals(MatchStatus.CONFLICT, match.status)
        assertEquals(null, match.drawableName)
    }
}
