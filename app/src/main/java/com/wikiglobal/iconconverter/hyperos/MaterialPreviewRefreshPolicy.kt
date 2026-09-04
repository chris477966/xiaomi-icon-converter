package com.wikiglobal.iconconverter.hyperos

/** Pure guard for the no-discovery, no-root System Monet preview refresh path. */
object MaterialPreviewRefreshPolicy {
    data class Plan(
        val rerender: Boolean,
        val metadataOnly: Boolean = false,
        val rootAccess: Boolean = false,
        val sourceDiscovery: Boolean = false,
        val stateCommits: Int = if (rerender || metadataOnly) 1 else 0
    )

    fun plan(
        colorMode: MaterialColorMode,
        currentPaletteHash: String?,
        newPaletteHash: String?,
        hasCachedGlyphs: Boolean,
        currentWallpaperStateHash: String? = null,
        newWallpaperStateHash: String? = null
    ): Plan {
        val rerender = colorMode != MaterialColorMode.CUSTOM &&
            hasCachedGlyphs &&
            newPaletteHash != null &&
            currentPaletteHash != newPaletteHash
        return Plan(
            rerender = rerender,
            metadataOnly = !rerender && colorMode == MaterialColorMode.WALLPAPER_AUTO &&
                currentWallpaperStateHash != newWallpaperStateHash && newWallpaperStateHash != null
        )
    }
    fun shouldRerender(
        colorMode: MaterialColorMode,
        currentPaletteHash: String?,
        newPaletteHash: String?,
        hasCachedGlyphs: Boolean
    ): Boolean = plan(colorMode, currentPaletteHash, newPaletteHash, hasCachedGlyphs).rerender
}
