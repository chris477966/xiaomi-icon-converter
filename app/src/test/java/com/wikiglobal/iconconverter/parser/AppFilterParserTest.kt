package com.wikiglobal.iconconverter.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class AppFilterParserTest {
    @Test fun `parses ComponentInfo values including leading dot activity`() {
        val xml = """<resources><item component="ComponentInfo{com.demo/.Launcher}" drawable="compiled_name_42" /></resources>"""
        val parsed = AppFilterParser.parse(ByteArrayInputStream(xml.toByteArray()))
        assertEquals("com.demo", parsed.mappings.single().component.packageName)
        assertEquals("com.demo.Launcher", parsed.mappings.single().component.activityName)
        assertEquals("compiled_name_42", parsed.mappings.single().drawableName)
    }

    @Test fun `parses calendar prefix and only existing days`() {
        val xml = """<resources><calendar component="ComponentInfo{com.calendar/.Main}" prefix="cal_" /></resources>"""
        val parsed = AppFilterParser.parse(ByteArrayInputStream(xml.toByteArray())) { it == "cal_1" || it == "cal_31" }
        assertEquals("cal_", parsed.calendars.single().prefix)
        assertEquals(setOf(1, 31), parsed.calendars.single().dayDrawables.keys)
        assertTrue(parsed.calendars.single().dayDrawables.values.all { it.startsWith("cal_") })
    }
}
