package com.wikiglobal.iconconverter.ui

import com.wikiglobal.iconconverter.hyperos.MaterialSourceOverride
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialOverrideStateTest {
    @Test fun `bottom sheet only enables available providers`() {
        val availability = MaterialSourceAvailability(native = true, lawnicons = false, aosp = true)
        assertTrue(availability.supports(MaterialSourceOverride.AUTO))
        assertTrue(availability.supports(MaterialSourceOverride.NATIVE))
        assertFalse(availability.supports(MaterialSourceOverride.LAWNICONS))
        assertTrue(availability.supports(MaterialSourceOverride.AOSP_FORCE))
        assertTrue(availability.supports(MaterialSourceOverride.KEEP))
    }

    @Test fun `single component update leaves other preview bytes unchanged`() {
        val previews = linkedMapOf("one#Activity" to byteArrayOf(1, 2), "two#Activity" to byteArrayOf(3, 4))
        val beforeOther = previews.getValue("two#Activity").copyOf()
        previews["one#Activity"] = byteArrayOf(9, 9)
        assertArrayEquals(beforeOther, previews.getValue("two#Activity"))
    }
}
