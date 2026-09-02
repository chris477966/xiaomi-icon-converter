package com.wikiglobal.iconconverter.matcher

import com.wikiglobal.iconconverter.model.CalendarMapping
import com.wikiglobal.iconconverter.model.IconMapping
import com.wikiglobal.iconconverter.model.IconMatch
import com.wikiglobal.iconconverter.model.InstalledApp
import com.wikiglobal.iconconverter.model.MatchConfidence
import com.wikiglobal.iconconverter.model.MatchStatus

/** Deterministic matching: a candidate set with different drawables is always a conflict, never a random icon. */
object IconMatcher {
    fun match(apps: List<InstalledApp>, mappings: List<IconMapping>, calendars: List<CalendarMapping> = emptyList()): List<IconMatch> =
        apps.map { app -> matchOne(app, mappings, calendars) }

    fun matchOne(app: InstalledApp, mappings: List<IconMapping>, calendars: List<CalendarMapping> = emptyList()): IconMatch {
        fun result(candidates: List<IconMapping>, status: MatchStatus, confidence: MatchConfidence, note: String): IconMatch? {
            if (candidates.isEmpty()) return null
            val names = candidates.map { it.drawableName }.distinct()
            val calendar = calendars.firstOrNull { it.component.packageName == app.packageName &&
                (it.component.activityName == app.launcherActivity || it.component.activityName in app.activityAliases || it.component.activityName == null) }
            return if (names.size > 1) IconMatch(app, MatchStatus.CONFLICT, MatchConfidence.NONE, detail = "同一匹配范围有 ${names.size} 个不同 drawable", dynamicCalendar = calendar)
            else IconMatch(app, status, confidence, names.single(), note, calendar)
        }
        val exact = mappings.filter { it.component.packageName == app.packageName && it.component.activityName == app.launcherActivity }
        result(exact, MatchStatus.EXACT_COMPONENT, MatchConfidence.HIGH, "component 精确匹配")?.let { return it }

        val alias = mappings.filter { it.component.packageName == app.packageName && it.component.activityName != app.launcherActivity && it.component.activityName in app.activityAliases }
        result(alias, MatchStatus.ALIAS, MatchConfidence.MEDIUM, "Launcher activity alias 匹配")?.let { return it }

        val packageOnly = mappings.filter { it.component.packageName == app.packageName && it.component.activityName == null }
        result(packageOnly, MatchStatus.PACKAGE, MatchConfidence.MEDIUM, "package 精确匹配")?.let { return it }

        val fallback = mappings.filter { it.component.packageName == app.packageName }
        result(fallback, MatchStatus.PACKAGE, MatchConfidence.LOW, "package fallback")?.let { return it }
        return IconMatch(app, MatchStatus.UNMATCHED, MatchConfidence.NONE, detail = "appfilter 中没有此包名")
    }
}
