package com.wikiglobal.iconconverter.hyperos

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialPreviewRefreshPolicyTest {
    @Test fun `SYSTEM_MONET_CHANGED_PREVIEW_RERENDER`() {
        assertTrue(MaterialPreviewRefreshPolicy.shouldRerender(MaterialColorMode.SYSTEM_MONET, "old", "new", true))
    }

    @Test fun `WALLPAPER_AUTO_CHANGED_PREVIEW_RERENDER`() {
        assertTrue(MaterialPreviewRefreshPolicy.shouldRerender(MaterialColorMode.WALLPAPER_AUTO, "old", "new", true))
    }

    @Test fun `SYSTEM_MONET_UNCHANGED_NO_RERENDER`() {
        assertFalse(MaterialPreviewRefreshPolicy.shouldRerender(MaterialColorMode.SYSTEM_MONET, "same", "same", true))
    }

    @Test fun `CUSTOM_MONET_CHANGE_NO_RERENDER`() {
        assertFalse(MaterialPreviewRefreshPolicy.shouldRerender(MaterialColorMode.CUSTOM, "old", "new", true))
    }

    @Test fun `no cached glyphs means no preview refresh`() {
        assertFalse(MaterialPreviewRefreshPolicy.shouldRerender(MaterialColorMode.SYSTEM_MONET, "old", "new", false))
    }

    @Test fun `unavailable palette means no preview refresh`() {
        assertFalse(MaterialPreviewRefreshPolicy.shouldRerender(MaterialColorMode.SYSTEM_MONET, "old", null, true))
    }

    @Test fun `PREVIEW_REFRESH_NO_ROOT_ACCESS_OR_SOURCE_DISCOVERY`() {
        val plan = MaterialPreviewRefreshPolicy.plan(MaterialColorMode.SYSTEM_MONET, "old", "new", true)
        assertFalse(plan.rootAccess)
        assertFalse(plan.sourceDiscovery)
        assertTrue(plan.rerender)
    }

    @Test fun `WALLPAPER_REFRESH_SINGLE_STATE_COMMIT`() {
        assertTrue(MaterialPreviewRefreshPolicy.plan(MaterialColorMode.SYSTEM_MONET, "old", "new", true).stateCommits == 1)
        assertTrue(MaterialPreviewRefreshPolicy.plan(MaterialColorMode.SYSTEM_MONET, "same", "same", true).stateCommits == 0)
    }
}
