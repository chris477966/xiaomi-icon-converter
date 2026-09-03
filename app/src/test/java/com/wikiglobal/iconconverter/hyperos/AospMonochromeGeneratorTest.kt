package com.wikiglobal.iconconverter.hyperos

import org.junit.Assert.*
import org.junit.Test

class AospMonochromeGeneratorTest {
    @Test fun `viewport preserves extra inset formula`() { val v=AospMonochromeGenerator.viewport(250,.25f);assertEquals(.6666667f,v.viewPortScale,.0001f);assertTrue(v.bitmapSize in 332..334);assertTrue(v.edgePixelLength>1) }
    @Test fun `normalization contrast and inversion are deterministic`() { val a=AospMonochromeGenerator.transform(intArrayOf(0,20,80,255,0,20,80,255),2)!!;val b=AospMonochromeGenerator.transform(intArrayOf(0,20,80,255,0,20,80,255),2)!!;assertArrayEquals(a,b);assertTrue(a.all{it in 0..255}) }
    @Test fun `uniform source is unavailable`() { assertNull(AospMonochromeGenerator.transform(IntArray(8){40},2)) }
    @Test fun `second contrast phase matches endpoints`() { assertEquals(0,AospMonochromeGenerator.secondContrast(0));assertEquals(255,AospMonochromeGenerator.secondContrast(255)) }
}
