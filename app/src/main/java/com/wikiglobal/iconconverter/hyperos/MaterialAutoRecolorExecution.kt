package com.wikiglobal.iconconverter.hyperos

/** Narrow write seam for tests; production delegates to the existing backup/install path. */
interface MaterialThemeAccess { fun copyCurrentTheme(): Boolean; fun installPatchedTheme(): Boolean; fun refreshIconCache(): Boolean }
object MaterialAutoRecolorExecution {
    fun execute(decision:MaterialAutoRecolorDecision,access:MaterialThemeAccess):Boolean {
        if(decision!=MaterialAutoRecolorDecision.RECOLOR)return false
        return access.copyCurrentTheme() && access.installPatchedTheme() && access.refreshIconCache()
    }
}
