package com.wikiglobal.iconconverter.hyperos

import com.wikiglobal.iconconverter.ui.MonetUiState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MonetPreviewBytesTest {
    @Test fun `preview bytes are the exact bytes retained for apply`() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val state = MonetUiState(generated = mapOf("pkg#Activity" to bytes), sources = mapOf("pkg#Activity" to MonetGlyphSource.NATIVE_MONOCHROME))
        assertArrayEquals(bytes, state.generated.getValue("pkg#Activity"))
    }
    @Test fun `unavailable source has no generated png`() {
        val state = MonetUiState(sources = mapOf("pkg#Activity" to MonetGlyphSource.UNAVAILABLE))
        assertFalse(state.generated.containsKey("pkg#Activity"))
    }
}
