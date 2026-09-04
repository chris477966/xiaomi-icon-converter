package com.wikiglobal.iconconverter.hyperos

enum class MaterialAutoRecolorDecision { DISABLED, NO_MATERIAL_INSTALL, PALETTE_UNCHANGED, ROOT_UNAVAILABLE, EXTERNAL_THEME_CHANGED, CACHE_UNAVAILABLE, RECOLOR }
data class MaterialAutoRecolorInput(val style: MaterialStyle, val installed: MaterialInstalledState, val paletteHash: String?, val currentThemeSha: String?, val rootAvailable: Boolean, val hasGlyphCache: Boolean)

/** Pure eligibility policy shared by runtime wallpaper events and foreground reconciliation. */
object MaterialAutoRecolorCoordinator {
    fun decide(input: MaterialAutoRecolorInput): MaterialAutoRecolorDecision = when {
        input.style.colorMode == MaterialColorMode.CUSTOM || !input.style.autoApplyWallpaperChanges -> MaterialAutoRecolorDecision.DISABLED
        !input.installed.isMaterialInstalled -> MaterialAutoRecolorDecision.NO_MATERIAL_INSTALL
        input.paletteHash == null || input.paletteHash == input.installed.paletteHash -> MaterialAutoRecolorDecision.PALETTE_UNCHANGED
        !input.rootAvailable -> MaterialAutoRecolorDecision.ROOT_UNAVAILABLE
        input.currentThemeSha == null || input.currentThemeSha != input.installed.themeArchiveShaAfterInstall -> MaterialAutoRecolorDecision.EXTERNAL_THEME_CHANGED
        !input.hasGlyphCache -> MaterialAutoRecolorDecision.CACHE_UNAVAILABLE
        else -> MaterialAutoRecolorDecision.RECOLOR
    }
}
