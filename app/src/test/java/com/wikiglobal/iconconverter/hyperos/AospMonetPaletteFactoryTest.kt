package com.wikiglobal.iconconverter.hyperos

import com.android.systemui.monet.ColorScheme
import com.android.systemui.monet.Shades
import com.android.systemui.monet.Style
import com.androidinternal.graphics.cam.CamUtils
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.TonalPalette
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AospMonetPaletteFactoryTest {
    private data class Fixture(val seed: Int, val roles: IntArray)

    private val fixtures = listOf(
        Fixture(0xffff0000.toInt(), intArrayOf(0xffffdad3.toInt(), 0xffffb4a6.toInt(), 0xff73342a.toInt(), 0xff571e17.toInt(), 0xffffdad3.toInt(), 0xff442925.toInt(), 0xfffbe0a6.toInt())),
        Fixture(0xff00ff00.toInt(), intArrayOf(0xffc0efb0.toInt(), 0xffa5d395.toInt(), 0xff285020.toInt(), 0xff12380c.toInt(), 0xffd7e7cd.toInt(), 0xff273422.toInt(), 0xffbcebee.toInt())),
        Fixture(0xff0000ff.toInt(), intArrayOf(0xffdfe0ff.toInt(), 0xffbec2ff.toInt(), 0xff3e4178.toInt(), 0xff272b60.toInt(), 0xffe1e0fa.toInt(), 0xff2e2f41.toInt(), 0xffffd7ef.toInt())),
        Fixture(0xff777777.toInt(), intArrayOf(0xffd8e2ff.toInt(), 0xffafc6ff.toInt(), 0xff2f4578.toInt(), 0xff162e60.toInt(), 0xffdce1f9.toInt(), 0xff2a3042.toInt(), 0xfffdd6fa.toInt())),
        Fixture(0xff228b22.toInt(), intArrayOf(0xffbfefb1.toInt(), 0xffa3d397.toInt(), 0xff275021.toInt(), 0xff0f380d.toInt(), 0xffd6e8cd.toInt(), 0xff263423.toInt(), 0xffbbebef.toInt())),
        Fixture(0xffff8800.toInt(), intArrayOf(0xffffdcc3.toInt(), 0xffffb77e.toInt(), 0xff6d3a09.toInt(), 0xff4f2500.toInt(), 0xffffdcc3.toInt(), 0xff422b1a.toInt(), 0xffe3e6af.toInt())),
        Fixture(0xff800080.toInt(), intArrayOf(0xffffd6f7.toInt(), 0xfff1b3e7.toInt(), 0xff653560.toInt(), 0xff4c1f48.toInt(), 0xfff7dbef.toInt(), 0xff3d2b3a.toInt(), 0xffffdacf.toInt())),
        Fixture(0xff336699.toInt(), intArrayOf(0xffcfe4ff.toInt(), 0xffa0cafd.toInt(), 0xff194974.toInt(), 0xff00325a.toInt(), 0xffd7e4f7.toInt(), 0xff253141.toInt(), 0xfff3daff.toInt()))
    )

    @Test fun `AOSP Tonal Spot fixed RGB fixtures`() {
        fixtures.forEach { fixture ->
            val p = AospMonetPaletteFactory.tonalSpot(fixture.seed)
            assertArrayEquals(fixture.roles, intArrayOf(p.accent1_100, p.accent1_200, p.accent1_700, p.accent1_800, p.accent2_100!!, p.accent2_800!!, p.accent3_100!!))
        }
    }

    @Test fun `factory delegates to AOSP ColorScheme`() {
        val scheme = ColorScheme(0xff336699.toInt(), Style.TONAL_SPOT)
        val p = AospMonetPaletteFactory.tonalSpot(0xff336699.toInt())
        assertEquals(scheme.accent1.s100, p.accent1_100)
        assertEquals(scheme.accent2.s800, p.accent2_800)
        assertEquals(scheme.accent3.s100, p.accent3_100)
    }

    @Test fun `AOSP Shades lstar and bright chroma cap`() {
        val shades = Shades.of(210f, 80f)
        assertEquals(99f, CamUtils.lstarFromInt(shades[0]), .25f)
        assertEquals(95f, CamUtils.lstarFromInt(shades[1]), .25f)
        assertEquals(90f, CamUtils.lstarFromInt(shades[2]), .25f)
        assertEquals(80f, CamUtils.lstarFromInt(shades[3]), .25f)
        assertEquals(49.6f, CamUtils.lstarFromInt(shades[6]), .25f)
        assertEquals(30f, CamUtils.lstarFromInt(shades[8]), .25f)
        assertEquals(20f, CamUtils.lstarFromInt(shades[9]), .25f)
        val capped = Shades.of(210f, 40f)
        val uncapped = Shades.of(210f, 80f)
        assertEquals(capped[0], uncapped[0])
        assertEquals(capped[1], uncapped[1])
    }

    @Test fun `AOSP and legacy Material Utilities RGB differ`() {
        val seed = 0xff336699.toInt()
        val legacy = Hct.fromInt(seed)
        val oldA1 = TonalPalette.fromHueAndChroma(legacy.hue, maxOf(48.0, legacy.chroma)).tone(90)
        assertNotEquals(oldA1, AospMonetPaletteFactory.tonalSpot(seed).accent1_100)
    }
}
