package com.wikiglobal.iconconverter.hyperos

import com.android.systemui.monet.ColorScheme
import com.android.systemui.monet.Style

/**
 * AOSP SystemUI Monet Tonal Spot palette. ColorScheme delegates shade generation
 * to the vendored AOSP Shades/CAM implementation; this is the sole seed-palette
 * implementation used by Wallpaper Auto and Custom modes.
 */
object AospMonetPaletteFactory {
    const val TONAL_SPOT_A1_CHROMA = 36.0
    const val TONAL_SPOT_A2_CHROMA = 16.0
    const val TONAL_SPOT_A3_CHROMA = 24.0
    const val TONAL_SPOT_A3_HUE_OFFSET = 60.0
    const val TONAL_SPOT_N1_CHROMA = 6.0
    const val TONAL_SPOT_N2_CHROMA = 8.0

    fun tonalSpot(seed: Int): MonetPalette {
        val scheme = ColorScheme(seed, Style.TONAL_SPOT)
        return MonetPalette(
            accent1_100 = scheme.accent1.s100,
            accent1_200 = scheme.accent1.s200,
            accent1_700 = scheme.accent1.s700,
            accent1_800 = scheme.accent1.s800,
            accent2_100 = scheme.accent2.s100,
            accent2_800 = scheme.accent2.s800,
            accent3_100 = scheme.accent3.s100
        )
    }
}
