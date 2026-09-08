package com.wikiglobal.iconconverter.iconpack

import android.graphics.drawable.ColorDrawable
import com.wikiglobal.iconconverter.model.AppIconAssignment
import com.wikiglobal.iconconverter.model.ComponentKey
import com.wikiglobal.iconconverter.model.IconAssignmentType
import com.wikiglobal.iconconverter.model.IconEntry
import com.wikiglobal.iconconverter.model.IconMapping
import com.wikiglobal.iconconverter.model.IconMatch
import com.wikiglobal.iconconverter.model.IconPack
import com.wikiglobal.iconconverter.model.InstalledApp
import com.wikiglobal.iconconverter.model.MatchConfidence
import com.wikiglobal.iconconverter.model.MatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconPackIndexTest {
    private fun app(packageName: String, label: String) = InstalledApp(packageName, "$packageName.Main", label, ColorDrawable())
    private fun pack(id: String, mappings: List<IconMapping> = emptyList(), entries: List<IconEntry>) = IconPack(
        displayName = id, packageName = id, mappings = mappings, calendars = emptyList(), effects = com.wikiglobal.iconconverter.model.IconEffects(), drawableLoader = { ColorDrawable() }, id = id, entries = entries
    )
    private fun entry(pack: String, name: String, packages: Set<String> = emptySet()) = IconEntry(pack, name, name.hashCode(), mappedPackageNames = packages, searchableKeywords = setOf(name) + packages)

    @Test fun `empty mapping still exposes every parsed resource`() {
        val icons = listOf(entry("pack", "telegram_alt"), entry("pack", "generic_star"))
        assertEquals(listOf("generic_star", "telegram_alt"), IconPackIndex.search(icons, "").map { it.resourceName }.sorted())
    }

    @Test fun `app label package and resource searches use the same index`() {
        val app = app("org.telegram.messenger", "Telegram")
        val indexed = IconPackIndex.enrich(pack("pack", entries = listOf(entry("pack", "telegram_alt", setOf(app.packageName)))), listOf(app)).entries
        assertEquals("telegram_alt", IconPackIndex.search(indexed, "Telegram", app).single().resourceName)
        assertEquals("telegram_alt", IconPackIndex.search(indexed, app.packageName, app).single().resourceName)
        assertEquals("telegram_alt", IconPackIndex.search(indexed, "telegram_alt", app).single().resourceName)
    }

    @Test fun `localized app label is added during reindex while resource alias remains searchable`() {
        val app = app("com.tencent.mm", "微信")
        val indexed = IconPackIndex.enrich(pack("pack", entries = listOf(entry("pack", "wechat_alt", setOf(app.packageName)))), listOf(app)).entries.single()
        assertTrue("微信" in indexed.searchableKeywords)
        assertEquals("wechat_alt", IconPackIndex.search(listOf(indexed), "微信", app).single().resourceName)
        assertEquals("wechat_alt", IconPackIndex.search(listOf(indexed), "WeChat", app).single().resourceName)
        assertEquals("wechat_alt", IconPackIndex.search(listOf(indexed), app.packageName, app).single().resourceName)
    }

    @Test fun `exact package mapping ranks before unrelated fuzzy resources`() {
        val app = app("com.demo", "Demo")
        val entries = listOf(entry("pack", "demo_generic", setOf("com.other")), entry("pack", "demo_exact", setOf("com.demo")))
        assertEquals("demo_exact", IconPackIndex.search(entries, "com.demo", app).first().resourceName)
    }

    @Test fun `same display names stay separated by package identity`() {
        val first = app("com.one", "Reader")
        val second = app("com.two", "Reader")
        val entries = listOf(entry("pack", "one_icon", setOf(first.packageName)), entry("pack", "two_icon", setOf(second.packageName)))
        assertEquals("one_icon", IconPackIndex.search(IconPackIndex.enrich(pack("pack", entries = entries), listOf(first, second)).entries, first.packageName, first).single().resourceName)
        assertEquals("two_icon", IconPackIndex.search(IconPackIndex.enrich(pack("pack", entries = entries), listOf(first, second)).entries, second.packageName, second).single().resourceName)
    }

    @Test fun `manual assignment can select another apps icon and wins automatic match`() {
        val current = app("com.current", "Current")
        val active = pack("pack-a", listOf(IconMapping(ComponentKey(current.packageName, null), "auto_icon")), listOf(entry("pack-a", "auto_icon", setOf(current.packageName))))
        val other = pack("pack-b", entries = listOf(entry("pack-b", "other_apps_icon", setOf("com.other"))))
        val legacy = IconMatch(current, MatchStatus.PACKAGE, MatchConfidence.MEDIUM, "auto_icon")
        val manual = AppIconAssignment(current.packageName, other.id, "other_apps_icon", IconAssignmentType.MANUAL, resourceIdentifier = "other_apps_icon".hashCode())
        val resolved = IconAssignmentResolver.resolve(current, active, mapOf(active.id to active, other.id to other), mapOf(current.packageName to manual), legacy)
        assertEquals("other_apps_icon", resolved.drawableName)
        assertEquals(IconAssignmentType.MANUAL, resolved.assignmentType)
        assertEquals(other.id, resolved.sourceIconPackId)
    }

    @Test fun `removing manual assignment restores automatic and switching active pack recalculates`() {
        val current = app("com.current", "Current")
        val first = pack("pack-a", listOf(IconMapping(ComponentKey(current.packageName, null), "first")), listOf(entry("pack-a", "first", setOf(current.packageName))))
        val second = pack("pack-b", listOf(IconMapping(ComponentKey(current.packageName, null), "second")), listOf(entry("pack-b", "second", setOf(current.packageName))))
        val autoFirst = IconAssignmentResolver.resolve(current, first, mapOf(first.id to first, second.id to second), emptyMap(), IconMatch(current, MatchStatus.PACKAGE, MatchConfidence.MEDIUM, "first"))
        val autoSecond = IconAssignmentResolver.resolve(current, second, mapOf(first.id to first, second.id to second), emptyMap(), IconMatch(current, MatchStatus.PACKAGE, MatchConfidence.MEDIUM, "second"))
        assertEquals("first", autoFirst.drawableName)
        assertEquals("second", autoSecond.drawableName)
        assertEquals(IconAssignmentType.AUTOMATIC, autoSecond.assignmentType)
        assertTrue(autoSecond.sourceIconPackId == second.id)
    }
}
