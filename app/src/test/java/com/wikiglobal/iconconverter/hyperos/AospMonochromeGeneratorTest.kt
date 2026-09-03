package com.wikiglobal.iconconverter.hyperos

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Test-only literal port of AOSP MonochromeIconFactory.generateMono pixel processing. */
private object AospReferenceMonochromeTransform {
    fun transform(pixels: ByteArray, edgePixelLength: Int): ByteArray? {
        var min = 0xFF; var max = 0
        for (byte in pixels) { min = minOf(min, byte.toInt() and 0xFF); max = maxOf(max, byte.toInt() and 0xFF) }
        if (min >= max) return null
        val range = max - min; val edge = edgePixelLength.coerceIn(0, pixels.size / 2); var sum = 0
        for (i in 0 until edge) { sum += pixels[i].toInt() and 0xFF; sum += pixels[pixels.size - 1 - i].toInt() and 0xFF }
        val edgeAverage = if (edge == 0) min.toFloat() else sum / (edge * 2f)
        val flipColor = (edgeAverage - min) / range > .5f
        val output = ByteArray(pixels.size)
        for (i in pixels.indices) { val p = pixels[i].toInt() and 0xFF; val p2 = Math.round((p - min) * 0xFF / range.toFloat()); output[i] = (if (flipColor) 255 - p2 else p2).toByte() }
        for (i in output.indices) { val p = output[i].toInt() and 0xFF; val p2 = if (p > 128) { val c = 1 - (p - 128).toDouble() / 128; 255 - (c * (255 - p)).toInt() } else { val c = 1 - (128 - p).toDouble() / 128; (c * p).toInt() }; output[i] = p2.coerceIn(0, 255).toByte() }
        return output
    }
}

class AospMonochromeGeneratorTest {
    @Test fun `viewport preserves AOSP extra inset formula`() { val v=AospMonochromeGenerator.viewport(250,.25f); assertEquals(.6666667f,v.viewPortScale,.0001f); assertEquals(333,v.bitmapSize); assertEquals(13819,v.edgePixelLength) }
    @Test fun `both adaptive layers use full bitmap bounds not centred icon bounds`() { val b=AospMonochromeGenerator.fullBitmapBounds(333); assertEquals(0,b.left); assertEquals(0,b.top); assertEquals(333,b.right); assertEquals(333,b.bottom) }
    @Test fun `AOSP production transform is byte equal to reference for synthetic icons`() {
        val cases=listOf(byteArrayOf(255.toByte(),255.toByte(),0,0,255.toByte(),255.toByte()),byteArrayOf(0,0,255.toByte(),255.toByte(),0,0),byteArrayOf(10,40,130.toByte(),220.toByte(),40,10),byteArrayOf(0,32,64,96,128.toByte(),160.toByte(),192.toByte(),255.toByte()),byteArrayOf(240.toByte(),240.toByte(),20,80,20,240.toByte(),240.toByte()),byteArrayOf(10,10,220.toByte(),120,220.toByte(),10,10))
        cases.forEach { source -> assertArrayEquals(AospReferenceMonochromeTransform.transform(source,2),AospMonochromeGenerator.transformPixels(source,2)) }
    }
    @Test fun `uniform source is unavailable`() { assertNull(AospMonochromeGenerator.transformPixels(ByteArray(8){40},2)) }
    @Test fun `second contrast keeps endpoints`() { assertEquals(0,AospMonochromeGenerator.secondContrast(0));assertEquals(255,AospMonochromeGenerator.secondContrast(255)) }
    @Test fun `edge inversion produces alpha`() { assertTrue(AospMonochromeGenerator.transformPixels(byteArrayOf(255.toByte(),255.toByte(),0,0,255.toByte(),255.toByte()),2)!!.any { (it.toInt() and 0xFF)>0 }) }
}
