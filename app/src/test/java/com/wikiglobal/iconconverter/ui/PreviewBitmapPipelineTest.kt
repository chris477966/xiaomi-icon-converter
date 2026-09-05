package com.wikiglobal.iconconverter.ui

import com.wikiglobal.iconconverter.hyperos.HyperOsThemeProfileDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PreviewBitmapPipelineTest {
    @Test fun `MATERIAL_PREVIEW_SIZE_80DP`() {
        assertEquals(80, PreviewBitmapPipeline.MATERIAL_PREVIEW_DP)
    }

    @Test fun `PREVIEW_BITMAP_SOURCE_uses_explicit_target_size`() {
        assertEquals(250, HyperOsThemeProfileDetector.FALLBACK_SIZE)
    }

    @Test fun `stable component keys preserve activity identity`() {
        assertNotEquals(
            componentPreviewKey("com.example", "com.example.FirstActivity"),
            componentPreviewKey("com.example", "com.example.SecondActivity")
        )
    }
}
