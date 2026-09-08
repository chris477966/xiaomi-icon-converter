package com.wikiglobal.iconconverter.autoadapt

import android.graphics.drawable.Drawable
import com.wikiglobal.iconconverter.model.ComponentKey
import com.wikiglobal.iconconverter.model.IconEffects
import com.wikiglobal.iconconverter.model.IconMapping
import com.wikiglobal.iconconverter.model.IconPack
import com.wikiglobal.iconconverter.model.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AutoAdaptCoreTest {
    private class Settings(var value:Boolean):AutoAdaptSettings { override fun enabled()=value }
    private class Pending(private val events:MutableMap<String,PendingPackageEvent> = linkedMapOf()):PendingPackageEvents {
        override fun record(packageName:String,eventType:AutoAdaptEventType):PendingPackageEvent { val old=events[packageName];val next=PendingPackageEvent(packageName,if(eventType==AutoAdaptEventType.REPLACED||old?.eventType==AutoAdaptEventType.REPLACED)AutoAdaptEventType.REPLACED else AutoAdaptEventType.NEW_INSTALL,maxOf((old?.timestamp?:0)+1,1));events[packageName]=next;return next }
        override fun snapshot()=events.values.sortedBy{it.packageName};override fun clearProcessed(snapshot:List<PendingPackageEvent>){snapshot.forEach{if(events[it.packageName]?.timestamp==it.timestamp)events.remove(it.packageName)}};override fun clear(){events.clear()}
    }
    private class Scheduler:AutoAdaptWorkScheduler { var enqueued=0;var cancelled=0;override fun enqueue(){enqueued++};override fun cancel(){cancelled++} }
    private class Sink(var fail:Boolean=false):AutoAdaptResultSink { var saved=0;override fun save(result:AutoAdaptDryRunResult,enabled:Boolean){if(fail)error("sink");saved++} }
    private fun app(activity:String="pkg.Main")=InstalledApp("pkg",activity,activity,mock(Drawable::class.java))
    private fun baseline():File { val file=File(Files.createTempDirectory("auto-baseline").toFile(),"icons");ZipOutputStream(file.outputStream()).use{out->out.putNextEntry(ZipEntry("res/drawable-xxhdpi/pkg.png"));out.write(byteArrayOf(1));out.closeEntry()};return file }
    private fun pack(vararg mappings:IconMapping)=IconPack("pack","pack",mappings.toList(),emptyList(),IconEffects()){null}
    private class Deps(val mode:ActiveThemeMode,val apps:List<InstalledApp>,val source:IconPackReadResult,val base:File?):AutoAdaptReadOnlyDependencies { override fun activeMode()=mode;override fun baseline()=base;override fun launcherApps(packageName:String)=apps;override fun selectedIconPack()=source }
    private fun engine(mode:ActiveThemeMode,apps:List<InstalledApp>,source:IconPackReadResult,base:File?=baseline())=AutoAdaptDryRunEngine(Deps(mode,apps,source,base))

    @Test fun PACKAGE_ADDED_NEW_INSTALL(){val pending=Pending();val scheduler=Scheduler();PackageEventHandler("self",Settings(true),pending,scheduler).handle(android.content.Intent.ACTION_PACKAGE_ADDED,"pkg",false);assertEquals(AutoAdaptEventType.NEW_INSTALL,pending.snapshot().single().eventType);assertEquals(1,scheduler.enqueued)}
    @Test fun PACKAGE_ADDED_REPLACING_BECOMES_REPLACED(){val pending=Pending();PackageEventHandler("self",Settings(true),pending,Scheduler()).handle(android.content.Intent.ACTION_PACKAGE_ADDED,"pkg",true);assertEquals(AutoAdaptEventType.REPLACED,pending.snapshot().single().eventType)}
    @Test fun PACKAGE_ADDED_PLUS_PACKAGE_REPLACED_DEDUPED(){val pending=Pending();val handler=PackageEventHandler("self",Settings(true),pending,Scheduler());handler.handle(android.content.Intent.ACTION_PACKAGE_ADDED,"pkg",false);handler.handle(android.content.Intent.ACTION_PACKAGE_REPLACED,"pkg",false);assertEquals(listOf(AutoAdaptEventType.REPLACED),pending.snapshot().map{it.eventType})}
    @Test fun MULTIPLE_PACKAGES_BATCHED(){val pending=Pending();val handler=PackageEventHandler("self",Settings(true),pending,Scheduler());handler.handle(android.content.Intent.ACTION_PACKAGE_ADDED,"a",false);handler.handle(android.content.Intent.ACTION_PACKAGE_ADDED,"b",false);assertEquals(setOf("a","b"),pending.snapshot().map{it.packageName}.toSet())}
    @Test fun SAME_PACKAGE_EVENT_UPDATED_DURING_WORK_NOT_LOST(){val pending=Pending();val snapshot=listOf(pending.record("pkg",AutoAdaptEventType.NEW_INSTALL));pending.record("pkg",AutoAdaptEventType.REPLACED);pending.clearProcessed(snapshot);assertEquals(AutoAdaptEventType.REPLACED,pending.snapshot().single().eventType)}
    @Test fun OWN_PACKAGE_IGNORED(){val pending=Pending();val scheduler=Scheduler();PackageEventHandler("self",Settings(true),pending,scheduler).handle(android.content.Intent.ACTION_PACKAGE_ADDED,"self",false);assertTrue(pending.snapshot().isEmpty());assertEquals(0,scheduler.enqueued)}
    @Test fun DISABLED_RECEIVER_NO_WORK(){val pending=Pending();val scheduler=Scheduler();PackageEventHandler("self",Settings(false),pending,scheduler).handle(android.content.Intent.ACTION_PACKAGE_ADDED,"pkg",false);assertTrue(pending.snapshot().isEmpty());assertEquals(0,scheduler.enqueued)}
    @Test fun PACKAGE_REMOVED_NOT_REGISTERED(){val pending=Pending();val scheduler=Scheduler();PackageEventHandler("self",Settings(true),pending,scheduler).handle(android.content.Intent.ACTION_PACKAGE_REMOVED,"pkg",false);assertTrue(pending.snapshot().isEmpty());assertEquals(0,scheduler.enqueued)}
    @Test fun ICON_PACK_MATCHED_COMPONENT_HAS_PATCH_ENTRIES(){val result=engine(ActiveThemeMode.ICON_PACK,listOf(app()),IconPackReadResult.Available(pack(IconMapping(ComponentKey("pkg","pkg.Main"),"icon")))).run(listOf(PendingPackageEvent("pkg",AutoAdaptEventType.NEW_INSTALL,1))).packages.single();assertEquals(AutoAdaptSourceStatus.ICON_PACK_MATCHED,result.routes.single().sourceStatus);assertTrue(result.routes.single().wouldPatchEntries.isNotEmpty())}
    @Test fun ICON_PACK_UNMATCHED_COMPONENT_HAS_NO_PATCH_ENTRIES(){val result=engine(ActiveThemeMode.ICON_PACK,listOf(app()),IconPackReadResult.Available(pack())).run(listOf(PendingPackageEvent("pkg",AutoAdaptEventType.NEW_INSTALL,1))).packages.single();assertEquals(AutoAdaptSourceStatus.ICON_PACK_UNMATCHED,result.routes.single().sourceStatus);assertTrue(result.routes.single().wouldPatchEntries.isEmpty())}
    @Test fun MIXED_MULTI_LAUNCHER_ONLY_MATCHED_COMPONENT_PATCHES(){val apps=listOf(app("pkg.Main"),app("pkg.Second"));val source=pack(IconMapping(ComponentKey("pkg","pkg.Main"),"first"),IconMapping(ComponentKey("pkg","pkg.Third"),"third"));val result=engine(ActiveThemeMode.ICON_PACK,apps,IconPackReadResult.Available(source)).run(listOf(PendingPackageEvent("pkg",AutoAdaptEventType.NEW_INSTALL,1))).packages.single();assertEquals(1,result.matchedComponents);assertEquals(1,result.conflictComponents);assertTrue(result.routes.single{it.launcherActivity=="pkg.Second"}.wouldPatchEntries.isEmpty())}
    @Test fun WOULD_PATCH_ENTRY_COUNT_UNIQUE(){val apps=listOf(app("pkg.A"),app("pkg.B"));val result=engine(ActiveThemeMode.ICON_PACK,apps,IconPackReadResult.Available(pack(IconMapping(ComponentKey("pkg",null),"icon")))).run(listOf(PendingPackageEvent("pkg",AutoAdaptEventType.NEW_INSTALL,1))).packages.single();assertEquals(result.routes.flatMap{it.wouldPatchEntries}.distinct().size,result.wouldPatchEntryCount)}
    @Test fun MATERIAL_MODE_ROUTE_DRY_RUN(){val result=engine(ActiveThemeMode.MATERIAL_YOU,listOf(app()),IconPackReadResult.Missing).run(listOf(PendingPackageEvent("pkg",AutoAdaptEventType.NEW_INSTALL,1))).packages.single();assertEquals(AutoAdaptSourceStatus.MATERIAL_RENDER_CHECK_DEFERRED,result.sourceStatus);assertTrue(result.routes.single().wouldPatchEntries.isEmpty())}
    @Test fun NO_ACTIVE_MODE_BLOCKED(){val result=engine(ActiveThemeMode.NONE,listOf(app()),IconPackReadResult.Missing).run(listOf(PendingPackageEvent("pkg",AutoAdaptEventType.NEW_INSTALL,1))).packages.single();assertEquals("NO_ACTIVE_APPLIED_MODE",result.blockReason)}
    @Test fun ICON_PACK_SHA_MISMATCH_BLOCKED(){val result=engine(ActiveThemeMode.ICON_PACK,listOf(app()),IconPackReadResult.ShaMismatch).run(listOf(PendingPackageEvent("pkg",AutoAdaptEventType.NEW_INSTALL,1))).packages.single();assertEquals("ICON_PACK_SOURCE_SHA_MISMATCH",result.blockReason)}
    @Test fun ICON_PACK_INVALID_APK_BLOCKED(){val result=engine(ActiveThemeMode.ICON_PACK,listOf(app()),IconPackReadResult.Invalid).run(listOf(PendingPackageEvent("pkg",AutoAdaptEventType.NEW_INSTALL,1))).packages.single();assertEquals("ICON_PACK_SOURCE_INVALID",result.blockReason)}
    @Test fun DISABLE_CANCELS_PENDING_WORK(){val scheduler=Scheduler();AutoAdaptDisableController(Pending(),scheduler).disable();assertEquals(1,scheduler.cancelled)}
    @Test fun DISABLE_CLEARS_PENDING_EVENTS(){val pending=Pending();pending.record("pkg",AutoAdaptEventType.NEW_INSTALL);AutoAdaptDisableController(pending,Scheduler()).disable();assertTrue(pending.snapshot().isEmpty())}
    @Test fun ENGINE_FAILURE_PRESERVES_PENDING(){val pending=Pending();val snapshot=listOf(pending.record("pkg",AutoAdaptEventType.NEW_INSTALL));val drain=AutoAdaptWorkerDrain(pending,Sink(),{true},{error("engine failure")},Scheduler());assertFalse(drain.drain(snapshot));assertEquals(snapshot,pending.snapshot())}
    @Test fun WORKER_SUCCESS_SAVES_REPORT_BEFORE_CLEARING_SNAPSHOT(){val pending=Pending();val scheduler=Scheduler();val snapshot=listOf(pending.record("pkg",AutoAdaptEventType.NEW_INSTALL));val sink=Sink();val result=AutoAdaptDryRunResult(1,emptyList());assertTrue(AutoAdaptWorkerDrain(pending,sink,{false},{result},scheduler).drain(snapshot));assertEquals(1,sink.saved);assertTrue(pending.snapshot().isEmpty())}
    @Test fun AUTO_DRY_RUN_NEVER_CALLS_ROOT(){assertNoForbiddenDependency("SuThemeRootExecutor","ThemeBackupManager")}
    @Test fun AUTO_DRY_RUN_NEVER_INSTALLS_THEME(){assertNoForbiddenDependency("ThemeApplicationPlanPatcher","ThemeApplyCompletion","ThemeBackupManager")}
    @Test fun AUTO_DRY_RUN_NEVER_REFRESHES_LAUNCHER(){assertNoForbiddenDependency("refreshIconCache","forceRestartLauncher")}
    @Test fun AUTO_DRY_RUN_NEVER_FORCE_STOPS_LAUNCHER(){assertNoForbiddenDependency("forceStop")}
    @Test fun AUTO_DRY_RUN_HAS_NO_ROOT_DEPENDENCY(){assertNoForbiddenDependency("SuThemeRootExecutor","ThemeBackupManager")}
    @Test fun AUTO_DRY_RUN_HAS_NO_THEME_INSTALL_DEPENDENCY(){assertNoForbiddenDependency("ThemeApplicationPlanPatcher","ThemeApplyCompletion","ThemeBackupManager")}
    private fun assertNoForbiddenDependency(vararg forbidden:String){listOf(AutoAdaptDryRunEngine::class.java,AutoAdaptDryRunWorker::class.java,PackageChangeReceiver::class.java).forEach{type->val resource=type.name.replace('.','/')+".class";val bytes=type.classLoader!!.getResourceAsStream(resource)!!.readBytes().toString(Charsets.ISO_8859_1);forbidden.forEach{assertFalse("$type references $it",bytes.contains(it))}}}
}
