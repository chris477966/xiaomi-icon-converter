package com.wikiglobal.iconconverter.hyperos

import com.wikiglobal.iconconverter.ui.MonetUiState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MonetPreviewBytesTest {
    @Test fun `preview bytes are the exact bytes retained for apply`() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val state = MonetUiState(generated = mapOf("pkg#Activity" to bytes), sources = mapOf("pkg#Activity" to MonetGlyphSource.NATIVE_MONOCHROME))
        assertArrayEquals(bytes, state.generated.getValue("pkg#Activity"))
    }
    @Test fun `unavailable source has no generated png`() {
        val state = MonetUiState(sources = mapOf("pkg#Activity" to MonetGlyphSource.UNAVAILABLE_NO_SOURCE))
        assertFalse(state.generated.containsKey("pkg#Activity"))
    }
    @Test fun `palette refresh preserves source and override state`() {
        val sources = mapOf("pkg#Activity" to MonetGlyphSource.NATIVE_MONOCHROME)
        val overrides = mapOf("pkg#Activity" to MaterialSourceOverride.NATIVE)
        val availability = mapOf("pkg#Activity" to com.wikiglobal.iconconverter.ui.MaterialSourceAvailability(native = true))
        val state = MonetUiState(sources = sources, overrides = overrides, availability = availability)
        val refreshed = state.copy(generated = mapOf("pkg#Activity" to byteArrayOf(9)))
        assertEquals(sources, refreshed.sources)
        assertEquals(overrides, refreshed.overrides)
        assertEquals(availability, refreshed.availability)
    }
}
