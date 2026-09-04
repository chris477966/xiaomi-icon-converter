package com.wikiglobal.iconconverter.hyperos

import org.junit.Assert.assertEquals
import org.junit.Test

class MaterialAutoRecolorCoordinatorTest {
    private val installed=MaterialInstalledState(isMaterialInstalled=true,paletteHash="old",themeArchiveShaAfterInstall="managed")
    private fun input(style:MaterialStyle=MaterialStyle(followWallpaperMonet=true),palette:String?="new",sha:String?="managed",root:Boolean=true,cache:Boolean=true)=MaterialAutoRecolorInput(style,installed,palette,sha,root,cache)
    @Test fun `follow enable after install works`()=assertEquals(MaterialAutoRecolorDecision.RECOLOR,MaterialAutoRecolorCoordinator.decide(input()))
    @Test fun `follow disable stops work`()=assertEquals(MaterialAutoRecolorDecision.DISABLED,MaterialAutoRecolorCoordinator.decide(input(style=MaterialStyle(followWallpaperMonet=false))))
    @Test fun `style store authority is represented by style input`()=assertEquals(MaterialAutoRecolorDecision.DISABLED,MaterialAutoRecolorCoordinator.decide(input(style=MaterialStyle(colorMode=MaterialColorMode.CUSTOM,followWallpaperMonet=true))))
    @Test fun `palette unchanged has no write`()=assertEquals(MaterialAutoRecolorDecision.PALETTE_UNCHANGED,MaterialAutoRecolorCoordinator.decide(input(palette="old")))
    @Test fun `external theme SHA disables recolor`()=assertEquals(MaterialAutoRecolorDecision.EXTERNAL_THEME_CHANGED,MaterialAutoRecolorCoordinator.decide(input(sha="external")))
    @Test fun `root unavailable has no write`()=assertEquals(MaterialAutoRecolorDecision.ROOT_UNAVAILABLE,MaterialAutoRecolorCoordinator.decide(input(root=false)))
    @Test fun `cache unavailable has no write`()=assertEquals(MaterialAutoRecolorDecision.CACHE_UNAVAILABLE,MaterialAutoRecolorCoordinator.decide(input(cache=false)))
}
