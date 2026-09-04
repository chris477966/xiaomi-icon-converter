package com.wikiglobal.iconconverter.hyperos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MaterialSourcePriorityTest {
    @Test fun `AUTO priority is Native then Lawnicons then AOSP then Keep`() {
        assertEquals("native", MaterialSourcePriority.automatic("native", "lawnicons", "aosp"))
        assertEquals("lawnicons", MaterialSourcePriority.automatic(null, "lawnicons", "aosp"))
        assertEquals("aosp", MaterialSourcePriority.automatic(null, null, "aosp"))
        assertNull(MaterialSourcePriority.automatic<String>(null, null, null))
    }
}
