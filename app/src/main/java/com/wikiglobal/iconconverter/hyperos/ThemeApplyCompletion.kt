package com.wikiglobal.iconconverter.hyperos

/** Runs only after ThemeBackupManager has verified the installed archive. It never force-stops Launcher. */
object ThemeApplyCompletion {
    fun softRefresh(root: ThemeRootExecutor): Boolean = root.refreshIconCache().success
}
