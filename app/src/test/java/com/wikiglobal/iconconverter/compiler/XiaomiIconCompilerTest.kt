package com.wikiglobal.iconconverter.compiler

import com.wikiglobal.iconconverter.model.XiaomiIconEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class XiaomiIconCompilerTest {
    private val entries = listOf(XiaomiIconEntry("com.tencent.mm", "com.tencent.mm.Main", byteArrayOf(1, 2, 3)))
    @Test fun `uses Xiaomi drawable archive path`() {
        assertEquals(listOf("res/drawable-xxhdpi/com.tencent.mm.png"), XiaomiIconCompiler.archivePaths(entries))
    }
    @Test fun `output is valid zip`() {
        val out = ByteArrayOutputStream(); XiaomiIconCompiler.writeIcons(out, entries)
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            assertEquals("res/drawable-xxhdpi/com.tencent.mm.png", zip.nextEntry.name)
            assertTrue(zip.readBytes().contentEquals(byteArrayOf(1, 2, 3)))
            assertEquals(null, zip.nextEntry)
        }
    }
}
