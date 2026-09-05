package com.wikiglobal.iconconverter.hyperos

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ThemeApplyCompletionTest {
    @Test fun `SOFT_REFRESH_AFTER_SUCCESSFUL_INSTALL`() {
        val root = FakeRoot(true)
        assertTrue(ThemeApplyCompletion.softRefresh(root)); assertTrue(root.refreshCalled)
    }
    @Test fun `REFRESH_FAILURE_NO_ROLLBACK`() {
        val root = FakeRoot(false)
        assertFalse(ThemeApplyCompletion.softRefresh(root)); assertTrue(root.refreshCalled); assertFalse(root.atomicCalled)
    }
    @Test fun `NO_FORCE_STOP_AFTER_APPLY`() {
        val root = FakeRoot(true)
        ThemeApplyCompletion.softRefresh(root)
        assertFalse(root.forceStopCalled)
    }
    private class FakeRoot(private val refreshSuccess: Boolean) : ThemeRootExecutor {
        var refreshCalled=false;var forceStopCalled=false;var atomicCalled=false
        override fun isRootAvailable()=true
        override fun inspect(path:String):ThemeFileMetadata?=null
        override fun copySystemFileTo(source:String,destination:File)=RootOperation(true)
        override fun atomicInstall(localArchive:File,target:String,original:ThemeFileMetadata):RootOperation{atomicCalled=true;return RootOperation(true)}
        override fun refreshIconCache():RootOperation{refreshCalled=true;return RootOperation(refreshSuccess)}
        override fun forceStopLauncher():RootOperation{forceStopCalled=true;return RootOperation(true)}
    }
}
