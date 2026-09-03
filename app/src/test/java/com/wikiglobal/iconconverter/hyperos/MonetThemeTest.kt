package com.wikiglobal.iconconverter.hyperos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MonetThemeTest {
    private val palette = MonetPalette(1,2,7,8,20,30)
    @Test fun `light and dark mapping is explicit`() { assertEquals(1 to 7, palette.colors(false)); assertEquals(7 to 2, palette.colors(true)) }
    @Test fun `palette hash changes with palette`() { assertNotEquals(palette.hash(), palette.copy(accent700 = 9).hash()) }
}
