package com.wikiglobal.iconconverter.hyperos

/** Pure guard for the no-discovery, no-root System Monet preview refresh path. */
object MaterialPreviewRefreshPolicy {
    fun shouldRerender(
        colorMode: MaterialColorMode,
        currentPaletteHash: String?,
        newPaletteHash: String?,
        hasCachedGlyphs: Boolean
    ): Boolean = colorMode == MaterialColorMode.SYSTEM_MONET &&
        hasCachedGlyphs &&
        newPaletteHash != null &&
        currentPaletteHash != newPaletteHash
}
