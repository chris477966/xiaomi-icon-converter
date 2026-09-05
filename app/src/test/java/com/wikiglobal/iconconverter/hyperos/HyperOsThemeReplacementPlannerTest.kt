package com.wikiglobal.iconconverter.hyperos

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class HyperOsThemeReplacementPlannerTest {
    @Test fun `SINGLE_ACTIVITY_EXISTING_ALIAS_IS_REPLACED`() {
        val dir = Files.createTempDirectory("single-alias").toFile(); val base = File(dir, "base"); val patched = File(dir, "patched")
        zip(base, mapOf("res/drawable-xxhdpi/pkg.png" to png(1), "res/drawable-xxhdpi/pkg.MainActivity.png" to png(2), "res/drawable-xxhdpi/pkg.other.png" to png(3)))
        val plan = HyperOsThemeReplacementPlanner.plan(base, listOf(RenderedThemeActivityIcon("pkg", "pkg.MainActivity", emptySet(), png(9))))
        HyperOs3ThemePatcher.patch(base, patched, plan.replacements, 250)
        ZipFile(patched).use { zip ->
            assertEntry(zip, "res/drawable-xxhdpi/pkg.png", png(9)); assertEntry(zip, "res/drawable-xxhdpi/pkg.MainActivity.png", png(9)); assertEntry(zip, "res/drawable-xxhdpi/pkg.other.png", png(3))
        }
    }

    @Test fun `MULTI_ACTIVITY_EXACT_ALIASES_AND_UNRELATED_ALIAS_UNCHANGED`() {
        val dir = Files.createTempDirectory("multi-alias").toFile(); val base = File(dir, "base"); val patched = File(dir, "patched")
        zip(base, mapOf("res/drawable-xxhdpi/pkg.png" to png(1), "res/drawable-xxhdpi/pkg.MainActivity.png" to png(2), "res/drawable-xxhdpi/pkg#SecondActivity.png" to png(3), "res/drawable-xxhdpi/pkg.other.png" to png(4)))
        val plan = HyperOsThemeReplacementPlanner.plan(base, listOf(RenderedThemeActivityIcon("pkg", "pkg.MainActivity", emptySet(), png(9)), RenderedThemeActivityIcon("pkg", "pkg.SecondActivity", emptySet(), png(8))))
        HyperOs3ThemePatcher.patch(base, patched, plan.replacements, 250)
        ZipFile(patched).use { zip ->
            assertEntry(zip, "res/drawable-xxhdpi/pkg.MainActivity.png", png(9)); assertEntry(zip, "res/drawable-xxhdpi/pkg#SecondActivity.png", png(8)); assertEntry(zip, "res/drawable-xxhdpi/pkg.other.png", png(4))
        }
    }
    @Test fun `WECHAT_FULL_NESTED_ACTIVITY`() {
        val dir=Files.createTempDirectory("wechat").toFile(); val base=File(dir,"base"); val patched=File(dir,"patched")
        zip(base,mapOf("res/drawable-xxhdpi/com.tencent.mm.png" to png(1),"res/drawable-xxhdpi/com.tencent.mm.ui.LauncherUI.png" to png(2),"res/drawable-xxhdpi/com.tencent.mm.ui.SettingsActivity.png" to png(3)))
        val plan=HyperOsThemeReplacementPlanner.plan(base,listOf(RenderedThemeActivityIcon("com.tencent.mm","com.tencent.mm.ui.LauncherUI",setOf("com.tencent.mm.ui.LauncherUI"),png(9))))
        HyperOs3ThemePatcher.patch(base,patched,plan.replacements,250); ZipFile(patched).use { z -> assertEntry(z,"res/drawable-xxhdpi/com.tencent.mm.png",png(9));assertEntry(z,"res/drawable-xxhdpi/com.tencent.mm.ui.LauncherUI.png",png(9));assertEntry(z,"res/drawable-xxhdpi/com.tencent.mm.ui.SettingsActivity.png",png(3)) }
    }
    @Test fun `ACTIVITY_ALIAS_TARGET_PAIR`() {
        val dir=Files.createTempDirectory("target").toFile();val base=File(dir,"base");zip(base,mapOf("res/drawable-xxhdpi/pkg.alias.HomeAlias.png" to png(1),"res/drawable-xxhdpi/pkg.real.HomeActivity.png" to png(2)))
        val plan=HyperOsThemeReplacementPlanner.plan(base,listOf(RenderedThemeActivityIcon("pkg","pkg.alias.HomeAlias",setOf("pkg.real.HomeActivity"),png(9))))
        assertEquals(setOf("res/drawable-xxhdpi/pkg.png","res/drawable-xxhdpi/pkg.alias.HomeAlias.png","res/drawable-xxhdpi/pkg.real.HomeActivity.png"),plan.replacements.map{it.entryName}.toSet())
    }
    @Test fun `MULTI_LAUNCHER_DETERMINISTIC_BASE`() {
        val dir=Files.createTempDirectory("multi-deterministic").toFile();val base=File(dir,"base");zip(base,emptyMap())
        val plan=HyperOsThemeReplacementPlanner.plan(base,listOf(RenderedThemeActivityIcon("pkg","pkg.Z",emptySet(),png(2)),RenderedThemeActivityIcon("pkg","pkg.A",emptySet(),png(1))))
        assertTrue(plan.replacements.first{it.entryName.endsWith("pkg.png")}.png.contentEquals(png(1)))
    }
    @Test fun `ENTRY_COLLISION_REJECTED`() {
        val dir=Files.createTempDirectory("collision").toFile();val base=File(dir,"base");zip(base,mapOf("res/drawable-xxhdpi/pkg.Shared.png" to png(1)))
        val plan=HyperOsThemeReplacementPlanner.plan(base,listOf(RenderedThemeActivityIcon("pkg","pkg.A",setOf("pkg.Shared"),png(2)),RenderedThemeActivityIcon("pkg","pkg.B",setOf("pkg.Shared"),png(3))))
        assertTrue(plan.entryConflicts.isNotEmpty())
    }
    @Test fun `PATCH_PLAN_BYTES_VERIFIED`() {
        val dir=Files.createTempDirectory("verify").toFile();val base=File(dir,"base");val patched=File(dir,"patched");zip(base,mapOf("transform_config.xml" to byteArrayOf(1)))
        val plan=HyperOsThemeReplacementPlanner.plan(base,listOf(RenderedThemeActivityIcon("pkg","pkg.Main",emptySet(),png(9))))
        HyperOs3ThemePatcher.patch(base,patched,plan.replacements,250);assertTrue(ThemeApplicationPlanVerifier.verify(patched,plan))
    }
    @Test fun `LARGE_MIXED_COMPONENT_COVERAGE`() {
        val dir=Files.createTempDirectory("large-plan").toFile();val base=File(dir,"base");val entries=linkedMapOf<String,ByteArray>();val icons=mutableListOf<RenderedThemeActivityIcon>()
        repeat(50){ index ->
            val pkg="pkg$index"; val activity=when(index%5){0->"$pkg.MainActivity";1->"$pkg.ui.Nested$index";2->"$pkg.alias.Home$index";else->"$pkg.Launch$index"}; val alias=when(index%5){2->setOf("$pkg.real.Target$index");else->emptySet()};val bytes=png(index+10)
            if(index%2==0)entries["res/drawable-xxhdpi/$activity.png"]=png(1)
            if(index%3==0)entries["res/drawable-xxhdpi/$pkg#${relativeActivity(pkg,activity)}.png"]=png(2)
            if(index%7==0)entries["res/drawable-xxhdpi/$pkg#${simpleActivity(pkg,activity)}.png"]=png(3)
            entries["res/drawable-xxhdpi/$pkg.Unrelated$index.png"]=png(4);icons+=RenderedThemeActivityIcon(pkg,activity,alias,bytes)
        }
        zip(base,entries);val plan=HyperOsThemeReplacementPlanner.plan(base,icons)
        assertEquals(50,plan.plannedComponentCount);assertTrue(plan.unroutedComponents.isEmpty());assertTrue(plan.entryConflicts.isEmpty())
    }
    @Test fun `GENERATED_EQUALS_PLANNED_COMPONENTS`() {
        val dir=Files.createTempDirectory("coverage-equality").toFile();val base=File(dir,"base");zip(base,emptyMap())
        val plan=HyperOsThemeReplacementPlanner.plan(base,listOf(RenderedThemeActivityIcon("a","a.Main",emptySet(),png(1)),RenderedThemeActivityIcon("b","b.Main",emptySet(),png(2))))
        assertEquals(plan.generatedComponentCount,plan.plannedComponentCount);assertTrue(plan.unroutedComponents.isEmpty())
    }
    private fun assertEntry(zip: ZipFile, name: String, expected: ByteArray) = assertTrue(zip.getInputStream(zip.getEntry(name)).readBytes().contentEquals(expected))
    private fun zip(file: File, contents: Map<String, ByteArray>) = ZipOutputStream(file.outputStream()).use { out -> contents.forEach { (name, bytes) -> out.putNextEntry(ZipEntry(name)); out.write(bytes); out.closeEntry() } }
    private fun png(marker:Int)=ByteArray(48).also { b -> byteArrayOf(-119,80,78,71,13,10,26,10).copyInto(b);b[12]=73;b[13]=72;b[14]=68;b[15]=82;b[17]=0;b[18]=0;b[19]=-6;b[21]=0;b[22]=0;b[23]=-6;b[24]=8;b[25]=6;b[47]=marker.toByte() }
}
