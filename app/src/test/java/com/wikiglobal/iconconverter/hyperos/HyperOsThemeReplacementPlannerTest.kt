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
    @Test fun `DIRECT_BEATS_TARGET_FALLBACK`() {
        val dir=Files.createTempDirectory("direct-wins").toFile();val base=File(dir,"base");zip(base,mapOf("res/drawable-xxhdpi/pkg.People.png" to png(1)))
        val plan=HyperOsThemeReplacementPlanner.plan(base,listOf(RenderedThemeActivityIcon("pkg","pkg.People",emptySet(),null,png(7)),RenderedThemeActivityIcon("pkg","pkg.Dialer",emptySet(),"pkg.People",png(8))))
        assertTrue(plan.entryConflicts.isEmpty());assertTrue(plan.replacements.first{it.entryName.endsWith("pkg.People.png")}.png.contentEquals(png(7)))
    }
    @Test fun `CONTACTS_PEOPLE_DIALER_NO_FALSE_CONFLICT`() {
        val dir=Files.createTempDirectory("contacts").toFile();val base=File(dir,"base");zip(base,mapOf("res/drawable-xxhdpi/com.android.contacts.png" to png(1),"res/drawable-xxhdpi/com.android.contacts.activities.PeopleActivity.png" to png(2),"res/drawable-xxhdpi/com.android.contacts.activities.TwelveKeyDialer.png" to png(3)))
        val people=png(7);val dialer=png(8);val plan=HyperOsThemeReplacementPlanner.plan(base,listOf(RenderedThemeActivityIcon("com.android.contacts","com.android.contacts.activities.PeopleActivity",emptySet(),null,people),RenderedThemeActivityIcon("com.android.contacts","com.android.contacts.activities.TwelveKeyDialer",emptySet(),"com.android.contacts.activities.PeopleActivity",dialer)))
        assertTrue(plan.entryConflicts.isEmpty());assertTrue(plan.replacements.first{it.entryName.endsWith("PeopleActivity.png")}.png.contentEquals(people));assertTrue(plan.replacements.first{it.entryName.endsWith("TwelveKeyDialer.png")}.png.contentEquals(dialer))
    }
    @Test fun `TARGET_FALLBACK_USED_WHEN_NO_DIRECT_OWNER`() {
        val dir=Files.createTempDirectory("fallback").toFile();val base=File(dir,"base");zip(base,mapOf("res/drawable-xxhdpi/pkg.real.Home.png" to png(1)));val plan=HyperOsThemeReplacementPlanner.plan(base,listOf(RenderedThemeActivityIcon("pkg","pkg.alias.Home",emptySet(),"pkg.real.Home",png(9))))
        assertTrue(plan.replacements.any{it.entryName.endsWith("pkg.real.Home.png")&&it.png.contentEquals(png(9))})
    }
    @Test fun `DIRECT_DIRECT_DIFFERENT_BYTES_CONFLICT`() { val d=Files.createTempDirectory("dd").toFile();val b=File(d,"base");zip(b,mapOf("res/drawable-xxhdpi/pkg#Shared.png" to png(1)));assertTrue(HyperOsThemeReplacementPlanner.plan(b,listOf(RenderedThemeActivityIcon("pkg","pkg.a.Shared",emptySet(),null,png(2)),RenderedThemeActivityIcon("pkg","pkg.b.Shared",emptySet(),null,png(3)))).entryConflicts.isNotEmpty()) }
    @Test fun `TARGET_TARGET_DIFFERENT_BYTES_CONFLICT`() { val d=Files.createTempDirectory("tt").toFile();val b=File(d,"base");zip(b,mapOf("res/drawable-xxhdpi/pkg.Target.png" to png(1)));assertTrue(HyperOsThemeReplacementPlanner.plan(b,listOf(RenderedThemeActivityIcon("pkg","pkg.A",emptySet(),"pkg.Target",png(2)),RenderedThemeActivityIcon("pkg","pkg.B",emptySet(),"pkg.Target",png(3)))).entryConflicts.isNotEmpty()) }
    @Test fun `MODE_SWITCH_MATERIAL_TO_ICON_PACK_PLAN`() { val d=Files.createTempDirectory("m2i").toFile();val b=File(d,"base");zip(b,emptyMap());assertEquals(1,HyperOsThemeReplacementPlanner.plan(b,listOf(RenderedThemeActivityIcon("pkg","pkg.Main",emptySet(),png(1)))).plannedComponentCount) }
    @Test fun `MODE_SWITCH_ICON_PACK_TO_MATERIAL_PLAN`() { val d=Files.createTempDirectory("i2m").toFile();val b=File(d,"base");zip(b,emptyMap());assertEquals(1,HyperOsThemeReplacementPlanner.plan(b,listOf(RenderedThemeActivityIcon("pkg","pkg.Main",emptySet(),png(2)))).plannedComponentCount) }
    @Test fun `GALLERY_UNIQUE_LEGACY_ALIAS_APPLIED`() {
        val d=Files.createTempDirectory("gallery-legacy").toFile();val b=File(d,"base");val p=File(d,"patched")
        zip(b,mapOf("res/drawable-xxhdpi/com.miui.gallery.png" to png(1),"res/drawable-xxhdpi/com.miui.gallery.activity.HomePageActivity.png" to png(2)))
        val plan=HyperOsThemeReplacementPlanner.plan(b,listOf(RenderedThemeActivityIcon("com.miui.gallery","com.miui.gallery.MainActivity",emptySet(),null,png(9))))
        val route=plan.components.single();assertTrue(route.directMatchedEntries.isEmpty());assertTrue(route.targetFallbackMatchedEntries.isEmpty());assertEquals(LegacyAliasStatus.UNIQUE,route.legacyAliasStatus)
        HyperOs3ThemePatcher.patch(b,p,plan.replacements,250);ZipFile(p).use { z -> assertEntry(z,"res/drawable-xxhdpi/com.miui.gallery.png",png(9));assertEntry(z,"res/drawable-xxhdpi/com.miui.gallery.activity.HomePageActivity.png",png(9)) }
    }
    @Test fun `DIRECT_ROUTE_DISALLOWS_LEGACY_HEURISTIC`() {
        val d=Files.createTempDirectory("direct-no-legacy").toFile();val b=File(d,"base");zip(b,mapOf("res/drawable-xxhdpi/pkg.Current.png" to png(1),"res/drawable-xxhdpi/pkg.Legacy.png" to png(2)))
        val route=HyperOsThemeReplacementPlanner.plan(b,listOf(RenderedThemeActivityIcon("pkg","pkg.Current",emptySet(),null,png(9)))).components.single()
        assertTrue(route.directMatchedEntries.isNotEmpty());assertTrue(route.legacyThemeAliasEntries.isEmpty());assertEquals(LegacyAliasStatus.NONE,route.legacyAliasStatus)
    }
    @Test fun `TARGET_ROUTE_DISALLOWS_LEGACY_HEURISTIC`() {
        val d=Files.createTempDirectory("target-no-legacy").toFile();val b=File(d,"base");zip(b,mapOf("res/drawable-xxhdpi/pkg.Target.png" to png(1),"res/drawable-xxhdpi/pkg.Legacy.png" to png(2)))
        val route=HyperOsThemeReplacementPlanner.plan(b,listOf(RenderedThemeActivityIcon("pkg","pkg.Current",emptySet(),"pkg.Target",png(9)))).components.single()
        assertTrue(route.directMatchedEntries.isEmpty());assertTrue(route.targetFallbackMatchedEntries.isNotEmpty());assertTrue(route.legacyThemeAliasEntries.isEmpty())
    }
    @Test fun `MULTIPLE_LEGACY_ALIASES_NOT_GUESSED`() {
        val d=Files.createTempDirectory("ambiguous-legacy").toFile();val b=File(d,"base");zip(b,mapOf("res/drawable-xxhdpi/pkg.png" to png(1),"res/drawable-xxhdpi/pkg.MainActivity.png" to png(2),"res/drawable-xxhdpi/pkg.SettingsActivity.png" to png(3)))
        val plan=HyperOsThemeReplacementPlanner.plan(b,listOf(RenderedThemeActivityIcon("pkg","pkg.NewMainActivity",emptySet(),null,png(9))));val route=plan.components.single()
        assertEquals(LegacyAliasStatus.AMBIGUOUS,route.legacyAliasStatus);assertTrue(route.legacyThemeAliasEntries.isEmpty());assertEquals(listOf("res/drawable-xxhdpi/pkg.png"),plan.replacements.map{it.entryName})
    }
    @Test fun `ZERO_LEGACY_ALIAS_PACKAGE_ONLY`() {
        val d=Files.createTempDirectory("zero-legacy").toFile();val b=File(d,"base");zip(b,mapOf("res/drawable-xxhdpi/pkg.png" to png(1)))
        val route=HyperOsThemeReplacementPlanner.plan(b,listOf(RenderedThemeActivityIcon("pkg","pkg.New",emptySet(),null,png(9)))).components.single()
        assertEquals(LegacyAliasStatus.NONE,route.legacyAliasStatus);assertEquals(listOf("res/drawable-xxhdpi/pkg.png"),route.finalReplacementEntries)
    }
    @Test fun `LEGACY_ALIAS_CANNOT_OVERRIDE_DIRECT_OWNER`() {
        val d=Files.createTempDirectory("legacy-owner").toFile();val b=File(d,"base");zip(b,mapOf("res/drawable-xxhdpi/pkg.Legacy.png" to png(1)))
        val direct=png(7);val plan=HyperOsThemeReplacementPlanner.plan(b,listOf(RenderedThemeActivityIcon("pkg","pkg.Legacy",emptySet(),null,direct),RenderedThemeActivityIcon("pkg","pkg.New",emptySet(),null,png(8))))
        assertTrue(plan.entryConflicts.isEmpty());assertTrue(plan.replacements.first{it.entryName.endsWith("pkg.Legacy.png")}.png.contentEquals(direct))
    }
    private fun assertEntry(zip: ZipFile, name: String, expected: ByteArray) = assertTrue(zip.getInputStream(zip.getEntry(name)).readBytes().contentEquals(expected))
    private fun zip(file: File, contents: Map<String, ByteArray>) = ZipOutputStream(file.outputStream()).use { out -> contents.forEach { (name, bytes) -> out.putNextEntry(ZipEntry(name)); out.write(bytes); out.closeEntry() } }
    private fun png(marker:Int)=ByteArray(48).also { b -> byteArrayOf(-119,80,78,71,13,10,26,10).copyInto(b);b[12]=73;b[13]=72;b[14]=68;b[15]=82;b[17]=0;b[18]=0;b[19]=-6;b[21]=0;b[22]=0;b[23]=-6;b[24]=8;b[25]=6;b[47]=marker.toByte() }
}
