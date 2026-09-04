package com.wikiglobal.iconconverter.hyperos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MaterialCandidateDiscoveryTest {
    @Test fun `activity bitmap then application adaptive makes AOSP available`() {
        val selected = MaterialCandidateDiscovery.firstAvailable(listOf(
            RawIconSource.ACTIVITY_RESOURCE to null,
            RawIconSource.APPLICATION_RESOURCE to "adaptive"
        ))
        assertEquals(RawIconSource.APPLICATION_RESOURCE, selected!!.first)
    }

    @Test fun `activity adaptive then application bitmap uses activity candidate`() {
        val selected = MaterialCandidateDiscovery.firstAvailable(listOf(
            RawIconSource.ACTIVITY_RESOURCE to "adaptive",
            RawIconSource.APPLICATION_RESOURCE to null
        ))
        assertEquals(RawIconSource.ACTIVITY_RESOURCE, selected!!.first)
    }

    @Test fun `application adaptive monochrome is discovered after activity legacy`() {
        val selected = MaterialCandidateDiscovery.firstAvailable(listOf(
            RawIconSource.ACTIVITY_RESOURCE to null,
            RawIconSource.APPLICATION_RESOURCE to "native-monochrome"
        ))
        assertEquals(RawIconSource.APPLICATION_RESOURCE, selected!!.first)
    }

    @Test fun `both legacy candidates leave AOSP unavailable`() {
        assertNull(MaterialCandidateDiscovery.firstAvailable<String>(listOf(
            RawIconSource.ACTIVITY_RESOURCE to null,
            RawIconSource.APPLICATION_RESOURCE to null,
            RawIconSource.ACTIVITY_FALLBACK to null,
            RawIconSource.APPLICATION_FALLBACK to null
        )))
    }
}
