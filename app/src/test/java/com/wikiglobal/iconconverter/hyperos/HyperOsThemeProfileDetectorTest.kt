package com.wikiglobal.iconconverter.hyperos

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class HyperOsThemeProfileDetectorTest {
    @Test fun `250 dominant archive selects 250`() = assertEquals(250, detect(mapOf("a" to 250, "b" to 250)).staticIconSize)
    @Test fun `288 dominant archive selects 288`() = assertEquals(288, detect(mapOf("a" to 288, "b" to 288)).staticIconSize)
    @Test fun `mixed archive selects most frequent static size`() = assertEquals(288, detect((1..100).associate { "a$it" to 288 } + (1..10).associate { "b$it" to 250 }).staticIconSize)
    @Test fun `transform only falls back to verified clean base size`() {
        val file = File(Files.createTempDirectory("profile").toFile(), "icons")
        ZipOutputStream(file.outputStream()).use { out -> out.putNextEntry(ZipEntry("transform_config.xml")); out.write(byteArrayOf(1)); out.closeEntry() }
        val profile = HyperOsThemeProfileDetector.detect(file)
        assertEquals(250, profile.staticIconSize); assertEquals(0, profile.detectedIconCount)
    }
    @Test fun `malformed and non square png are ignored`() {
        val file = File(Files.createTempDirectory("profile").toFile(), "icons")
        ZipOutputStream(file.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("res/drawable-xxhdpi/bad.png")); out.write(byteArrayOf(1)); out.closeEntry()
            out.putNextEntry(ZipEntry("res/drawable-xxhdpi/non-square.png")); out.write(png(250, 288)); out.closeEntry()
        }
        assertEquals(250, HyperOsThemeProfileDetector.detect(file).staticIconSize)
    }
    private fun detect(sizes: Map<String, Int>): HyperOsThemeProfile {
        val file = File(Files.createTempDirectory("profile").toFile(), "icons")
        ZipOutputStream(file.outputStream()).use { out -> sizes.forEach { (name, size) -> out.putNextEntry(ZipEntry("res/drawable-xxhdpi/$name.png")); out.write(png(size, size)); out.closeEntry() } }
        return HyperOsThemeProfileDetector.detect(file)
    }
    private fun png(w: Int, h: Int) = ByteArray(48).also { b -> byteArrayOf(-119,80,78,71,13,10,26,10).copyInto(b); b[12]=73;b[13]=72;b[14]=68;b[15]=82; fun put(i:Int,v:Int){b[i]=(v ushr 24).toByte();b[i+1]=(v ushr 16).toByte();b[i+2]=(v ushr 8).toByte();b[i+3]=v.toByte()};put(16,w);put(20,h);b[24]=8;b[25]=6 }
}
