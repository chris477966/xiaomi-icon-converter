package com.wikiglobal.iconconverter.parser

import com.wikiglobal.iconconverter.model.CalendarMapping
import com.wikiglobal.iconconverter.model.ComponentKey
import com.wikiglobal.iconconverter.model.IconEffects
import com.wikiglobal.iconconverter.model.IconMapping
import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Pure XML parser, intentionally independent from APK storage and Android resource access. */
object AppFilterParser {
    fun parse(input: InputStream, drawableExists: (String) -> Boolean = { true }): ParsedAppFilter {
        val mappings = mutableListOf<IconMapping>()
        val calendars = mutableListOf<CalendarMapping>()
        val backs = mutableListOf<String>(); val masks = mutableListOf<String>(); val upons = mutableListOf<String>()
        var scale: Float? = null
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val nodes = factory.newDocumentBuilder().parse(input).getElementsByTagName("*")
        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as? Element ?: continue
            val tag = element.tagName.lowercase()
            when (tag) {
                "item" -> {
                    val component = parseComponent(element.attr("component"))
                    val drawable = element.attr("drawable")?.normalizedDrawableName()
                    if (component != null && !drawable.isNullOrBlank()) mappings += IconMapping(component, drawable)
                }
                "calendar" -> {
                    val component = parseComponent(element.attr("component"))
                    val prefix = element.attr("prefix")?.trim()
                    if (component != null && !prefix.isNullOrBlank()) {
                        val days = (1..31).mapNotNull { day -> "$prefix$day".takeIf(drawableExists)?.let { day to it } }.toMap()
                        calendars += CalendarMapping(component, prefix, days)
                    }
                }
                "iconback" -> backs += element.drawableValues()
                "iconmask" -> masks += element.drawableValues()
                "iconupon" -> upons += element.drawableValues()
                "scale" -> scale = element.attr("factor")?.toFloatOrNull() ?: element.attr("scale")?.toFloatOrNull()
            }
        }
        return ParsedAppFilter(mappings, calendars, IconEffects(backs, masks, upons, scale))
    }

    /** Parses ComponentInfo{com.example/.MainActivity}, ComponentInfo{com.example/com.example.MainActivity}, or package-only values. */
    fun parseComponent(raw: String?): ComponentKey? {
        val value = raw?.trim()?.removePrefix("ComponentInfo{")?.removeSuffix("}") ?: return null
        if (value.isBlank()) return null
        val slash = value.indexOf('/')
        return if (slash < 0) ComponentKey.normalized(value, null)
        else ComponentKey.normalized(value.substring(0, slash), value.substring(slash + 1).ifBlank { null })
    }

    private fun Element.attr(name: String): String? = (0 until attributes.length).firstOrNull { i ->
        attributes.item(i).nodeName.equals(name, true)
    }?.let { attributes.item(it).nodeValue }
    private fun Element.drawableValues(): List<String> = (0 until attributes.length).mapNotNull { i ->
        attributes.item(i).let { attribute ->
            attribute.nodeName.takeIf { it.startsWith("img", true) || it.equals("drawable", true) }?.let { attribute.nodeValue }
        }
    }

    private fun String.normalizedDrawableName(): String {
        val value = trim().removePrefix("@")
        return value.substringAfterLast('/').substringBeforeLast('.').removeSuffix(".9")
    }
}

data class ParsedAppFilter(val mappings: List<IconMapping>, val calendars: List<CalendarMapping>, val effects: IconEffects)
