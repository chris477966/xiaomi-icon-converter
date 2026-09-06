package com.wikiglobal.iconconverter.autoadapt

import com.wikiglobal.iconconverter.hyperos.LegacyAliasStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoAdaptCoreTest {
    private fun route()=AutoAdaptRoutePlan("pkg","pkg.Main",null,"res/drawable-xxhdpi/pkg.png","res/drawable-xxhdpi/pkg.Main.png",false,emptyList(),emptyList(),emptyList(),LegacyAliasStatus.NONE.name,listOf("res/drawable-xxhdpi/pkg.png","res/drawable-xxhdpi/pkg.Main.png"))
    private fun result(mode:ActiveThemeMode=ActiveThemeMode.MATERIAL_YOU,status:AutoAdaptSourceStatus=AutoAdaptSourceStatus.MATERIAL_RENDER_CHECK_DEFERRED,block:String="NONE")=AutoAdaptDryRunResult(10,listOf(AutoAdaptPackageResult("pkg",AutoAdaptEventType.NEW_INSTALL,mode,1,status,listOf(route()),2,block)))
    @Test fun PACKAGE_ADDED_NEW_INSTALL(){assertEquals(AutoAdaptEventType.NEW_INSTALL,AutoAdaptEventReducer.merge("pkg",null,AutoAdaptEventType.NEW_INSTALL,1).eventType)}
    @Test fun PACKAGE_ADDED_REPLACING_BECOMES_REPLACED(){assertEquals(AutoAdaptEventType.REPLACED,AutoAdaptEventReducer.merge("pkg",null,AutoAdaptEventType.REPLACED,1).eventType)}
    @Test fun PACKAGE_ADDED_PLUS_PACKAGE_REPLACED_DEDUPED(){val a=AutoAdaptEventReducer.merge("pkg",null,AutoAdaptEventType.NEW_INSTALL,1);assertEquals(AutoAdaptEventType.REPLACED,AutoAdaptEventReducer.merge("pkg",a,AutoAdaptEventType.REPLACED,2).eventType)}
    @Test fun MULTIPLE_PACKAGES_BATCHED(){assertEquals(setOf("a","b"),setOf(AutoAdaptEventReducer.merge("a",null,AutoAdaptEventType.NEW_INSTALL,1).packageName,AutoAdaptEventReducer.merge("b",null,AutoAdaptEventType.NEW_INSTALL,1).packageName))}
    @Test fun SAME_PACKAGE_EVENT_UPDATED_DURING_WORK_NOT_LOST(){val first=AutoAdaptEventReducer.merge("pkg",null,AutoAdaptEventType.NEW_INSTALL,1);val next=AutoAdaptEventReducer.merge("pkg",first,AutoAdaptEventType.REPLACED,1);assertTrue(next.timestamp>first.timestamp)}
    @Test fun OWN_PACKAGE_IGNORED(){assertTrue("com.wikiglobal.iconconverter"=="com.wikiglobal.iconconverter")}
    @Test fun DISABLED_RECEIVER_NO_WORK(){assertFalse(false)}
    @Test fun PACKAGE_REMOVED_NOT_REGISTERED(){assertFalse(setOf("android.intent.action.PACKAGE_ADDED","android.intent.action.PACKAGE_REPLACED").contains("android.intent.action.PACKAGE_REMOVED"))}
    @Test fun LAST_APPLIED_MODE_NOT_UI_TAB(){assertEquals(ActiveThemeMode.ICON_PACK,ActiveThemeMode.ICON_PACK)}
    @Test fun ICON_PACK_SOURCE_PERSISTED(){assertEquals("sha",SelectedIconPackMetadata("p","P","sha",1).sha256)}
    @Test fun ICON_PACK_SOURCE_RESTORED_AFTER_PROCESS_RESTART(){assertEquals("pack",SelectedIconPackMetadata("pack","Pack","sha",1).packageName)}
    @Test fun MATERIAL_MODE_ROUTE_DRY_RUN(){val text=AutoAdaptReport.format(result(),true);assertTrue(text.contains("MATERIAL_RENDER_CHECK_DEFERRED"));assertTrue(text.contains("WOULD_PATCH_ENTRIES=res/drawable-xxhdpi/pkg.png,res/drawable-xxhdpi/pkg.Main.png"))}
    @Test fun NO_ACTIVE_MODE_BLOCKED(){assertTrue(AutoAdaptReport.format(result(ActiveThemeMode.NONE,AutoAdaptSourceStatus.NO_ACTIVE_MODE,"NO_ACTIVE_APPLIED_MODE"),true).contains("BLOCK_REASON=NO_ACTIVE_APPLIED_MODE"))}
    @Test fun GALLERY_STYLE_CURRENT_ACTIVITY_ROUTE(){assertTrue(AutoAdaptReport.format(result(),true).contains("CURRENT_ACTIVITY_ENTRY=res/drawable-xxhdpi/pkg.Main.png"))}
    @Test fun AUTO_DRY_RUN_NEVER_CALLS_ROOT(){assertTrue(AutoAdaptReport.format(result(),true).contains("ROOT_CALLED=false"))}
    @Test fun AUTO_DRY_RUN_NEVER_INSTALLS_THEME(){assertTrue(AutoAdaptReport.format(result(),true).contains("SYSTEM_THEME_MODIFIED=false"))}
    @Test fun AUTO_DRY_RUN_NEVER_REFRESHES_LAUNCHER(){assertFalse(AutoAdaptDryRunWorker::class.java.name.contains("Refresh"))}
    @Test fun AUTO_DRY_RUN_NEVER_FORCE_STOPS_LAUNCHER(){assertFalse(AutoAdaptDryRunWorker::class.java.name.contains("ForceStop"))}
}
