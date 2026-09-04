package com.wikiglobal.iconconverter.hyperos

import org.junit.Assert.assertEquals
import org.junit.Test

class MaterialAutoRecolorExecutionTest {
    private class Fake:MaterialThemeAccess{var copies=0;var installs=0;var refreshes=0;override fun copyCurrentTheme()=true.also{copies++};override fun installPatchedTheme()=true.also{installs++};override fun refreshIconCache()=true.also{refreshes++}}
    @Test fun `non recolor decisions never write`() { listOf(MaterialAutoRecolorDecision.PALETTE_UNCHANGED,MaterialAutoRecolorDecision.ROOT_UNAVAILABLE,MaterialAutoRecolorDecision.EXTERNAL_THEME_CHANGED,MaterialAutoRecolorDecision.CACHE_UNAVAILABLE).forEach{d->val f=Fake();MaterialAutoRecolorExecution.execute(d,f);assertEquals(0,f.copies);assertEquals(0,f.installs)} }
    @Test fun `recolor installs exactly once`() { val f=Fake();MaterialAutoRecolorExecution.execute(MaterialAutoRecolorDecision.RECOLOR,f);assertEquals(1,f.copies);assertEquals(1,f.installs) }
}
