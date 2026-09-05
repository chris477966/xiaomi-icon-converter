package com.wikiglobal.iconconverter.hyperos

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class FullApplyBaselineTest {
    @Test fun `full apply does not stack transformed archives`() {
        val dir = Files.createTempDirectory("full-apply").toFile(); val base=File(dir,"base"); val first=File(dir,"first"); val second=File(dir,"second")
        zip(base, mapOf("res/drawable-xxhdpi/A.png" to png(1), "res/drawable-xxhdpi/B.png" to png(2), "transform_config.xml" to byteArrayOf(9)))
        HyperOs3ThemePatcher.patch(base, first, listOf(HyperOs3IconReplacement("A",png(3)), HyperOs3IconReplacement("B",png(4))),250)
        // Pack B is intentionally patched from the original baseline, not `first`.
        HyperOs3ThemePatcher.patch(base, second, listOf(HyperOs3IconReplacement("A",png(5))),250)
        ZipFile(second).use { zip -> assertTrue(zip.getInputStream(zip.getEntry("res/drawable-xxhdpi/A.png")).readBytes().contentEquals(png(5))); assertTrue(zip.getInputStream(zip.getEntry("res/drawable-xxhdpi/B.png")).readBytes().contentEquals(png(2))) }
    }
    private fun zip(f:File, values:Map<String,ByteArray>)=ZipOutputStream(f.outputStream()).use{out->values.forEach{(n,b)->out.putNextEntry(ZipEntry(n));out.write(b);out.closeEntry()}}
    private fun png(marker:Int)=ByteArray(48).also { b -> byteArrayOf(-119,80,78,71,13,10,26,10).copyInto(b);b[12]=73;b[13]=72;b[14]=68;b[15]=82;b[17]=0;b[18]=0;b[19]=-6;b[21]=0;b[22]=0;b[23]=-6;b[24]=8;b[25]=6;b[47]=marker.toByte() }
}
