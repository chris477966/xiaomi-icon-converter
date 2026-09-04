package com.wikiglobal.iconconverter.hyperos

/** Pure guard for the no-discovery, no-root System Monet preview refresh path. */
object MaterialPreviewRefreshPolicy {
    data class Plan(
        val rerender: Boolean,
        val rootAccess: Boolean = false,
        val sourceDiscovery: Boolean = false,
        val stateCommits: Int = if (rerender) 1 else 0
    )

    fun plan(
        colorMode: MaterialColorMode,
        currentPaletteHash: String?,
        newPaletteHash: String?,
        hasCachedGlyphs: Boolean
    ) = Plan(
        rerender = colorMode == MaterialColorMode.SYSTEM_MONET &&
            hasCachedGlyphs &&
            newPaletteHash != null &&
            currentPaletteHash != newPaletteHash
    )

    fun shouldRerender(
        colorMode: MaterialColorMode,
        currentPaletteHash: String?,
        newPaletteHash: String?,
        hasCachedGlyphs: Boolean
    ): Boolean = plan(colorMode, currentPaletteHash, newPaletteHash, hasCachedGlyphs).rerender
}
