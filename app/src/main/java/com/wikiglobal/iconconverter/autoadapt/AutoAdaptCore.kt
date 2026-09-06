package com.wikiglobal.iconconverter.autoadapt

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wikiglobal.iconconverter.hyperos.ComponentThemeRoute
import com.wikiglobal.iconconverter.hyperos.FileThemeSessionStore
import com.wikiglobal.iconconverter.hyperos.HyperOsThemeArchiveIndex
import com.wikiglobal.iconconverter.hyperos.HyperOsThemeEntryResolver
import com.wikiglobal.iconconverter.matcher.IconMatcher
import com.wikiglobal.iconconverter.model.MatchStatus
import com.wikiglobal.iconconverter.parser.IconPackParser
import com.wikiglobal.iconconverter.repository.InstalledAppsRepository
import java.io.File
import java.util.concurrent.TimeUnit

enum class AutoAdaptEventType { NEW_INSTALL, REPLACED }
enum class ActiveThemeMode { NONE, ICON_PACK, MATERIAL_YOU }
enum class AutoAdaptSourceStatus { NOT_CHECKED, ICON_PACK_MATCHED, ICON_PACK_UNMATCHED, ICON_PACK_CONFLICT, MATERIAL_RENDER_CHECK_DEFERRED, NO_ACTIVE_MODE, SOURCE_UNAVAILABLE }
data class PendingPackageEvent(val packageName:String,val eventType:AutoAdaptEventType,val timestamp:Long)
object AutoAdaptEventReducer { fun merge(packageName:String,existing:PendingPackageEvent?,incoming:AutoAdaptEventType,now:Long):PendingPackageEvent { val type=if(incoming==AutoAdaptEventType.REPLACED||existing?.eventType==AutoAdaptEventType.REPLACED)AutoAdaptEventType.REPLACED else AutoAdaptEventType.NEW_INSTALL;return PendingPackageEvent(packageName,type,maxOf(now,(existing?.timestamp?:0)+1)) } }
data class AutoAdaptRoutePlan(val packageName:String,val launcherActivity:String,val targetActivity:String?,val packageEntry:String,val currentActivityEntry:String,val currentActivityEntryExisted:Boolean,val directMatchedEntries:List<String>,val targetFallbackMatchedEntries:List<String>,val legacyThemeAliasEntries:List<String>,val legacyAliasStatus:String,val wouldPatchEntries:List<String>)
data class AutoAdaptPackageResult(val packageName:String,val eventType:AutoAdaptEventType,val activeMode:ActiveThemeMode,val launcherComponentCount:Int,val sourceStatus:AutoAdaptSourceStatus,val routes:List<AutoAdaptRoutePlan>,val wouldPatchEntryCount:Int,val blockReason:String)
data class AutoAdaptDryRunResult(val timestamp:Long,val packages:List<AutoAdaptPackageResult>)

class AutoAdaptSettingsStore(context:Context) { private val prefs=context.getSharedPreferences("auto-adapt-settings",Context.MODE_PRIVATE); fun enabled()=prefs.getBoolean("enabled",false); fun setEnabled(enabled:Boolean){prefs.edit().putBoolean("enabled",enabled).apply()} }
class ActiveThemeModeStore(context:Context) { private val prefs=context.getSharedPreferences("active-theme-mode",Context.MODE_PRIVATE); fun get()=runCatching{ActiveThemeMode.valueOf(prefs.getString("mode",ActiveThemeMode.NONE.name)!!)}.getOrDefault(ActiveThemeMode.NONE); fun set(mode:ActiveThemeMode){prefs.edit().putString("mode",mode.name).apply()} }
class PendingPackageStore(context:Context) {
    private val prefs=context.getSharedPreferences("auto-adapt-pending",Context.MODE_PRIVATE)
    fun snapshot():List<PendingPackageEvent> = prefs.all.mapNotNull { (pkg,value) -> (value as? String)?.split('|')?.takeIf{it.size==2}?.let { parts -> runCatching { PendingPackageEvent(pkg,AutoAdaptEventType.valueOf(parts[0]),parts[1].toLong()) }.getOrNull() } }.sortedBy { it.packageName }
    fun record(packageName:String,eventType:AutoAdaptEventType):PendingPackageEvent { val next=AutoAdaptEventReducer.merge(packageName,snapshot().firstOrNull{it.packageName==packageName},eventType,System.currentTimeMillis());prefs.edit().putString(packageName,"${next.eventType.name}|${next.timestamp}").apply();return next }
    fun clearProcessed(snapshot:List<PendingPackageEvent>) { val current=snapshot().associateBy{it.packageName};val edit=prefs.edit();snapshot.forEach{event->if(current[event.packageName]?.timestamp==event.timestamp)edit.remove(event.packageName)};edit.apply() }
}
data class SelectedIconPackMetadata(val packageName:String,val displayName:String,val sha256:String,val importedAt:Long)
class SelectedIconPackStore(private val context:Context) {
    private val dir=File(context.filesDir,"icon-pack");private val apk get()=File(dir,"selected.apk");private val prefs=context.getSharedPreferences("selected-icon-pack",Context.MODE_PRIVATE)
    fun copyToTemporary(uri:Uri):File { dir.mkdirs();val temp=File(dir,"selected.apk.tmp");context.contentResolver.openInputStream(uri)?.use{input->temp.outputStream().use(input::copyTo)}?:error("无法读取所选 APK");return temp }
    fun commitTemporary(temp:File,metadata:SelectedIconPackMetadata):File { val target=apk;if(target.exists())target.delete();check(temp.renameTo(target)){"无法保存图标包"};prefs.edit().putString("package",metadata.packageName).putString("label",metadata.displayName).putString("sha",metadata.sha256).putLong("imported",metadata.importedAt).apply();return target }
    fun selectedApk():File?=apk.takeIf{it.isFile}
    fun metadata():SelectedIconPackMetadata?=selectedApk()?.let{file->SelectedIconPackMetadata(prefs.getString("package","")!!,prefs.getString("label","")!!,prefs.getString("sha","")!!,prefs.getLong("imported",0))}
}
class AutoAdaptDryRunStore(context:Context) { private val prefs=context.getSharedPreferences("auto-adapt-result",Context.MODE_PRIVATE);fun save(result:AutoAdaptDryRunResult){prefs.edit().putString("report",AutoAdaptReport.format(result,true)).putLong("time",result.timestamp).putInt("packages",result.packages.size).putInt("matched",result.packages.count{it.sourceStatus==AutoAdaptSourceStatus.ICON_PACK_MATCHED}).putInt("blocked",result.packages.count{it.blockReason!="NONE"}).apply()};fun report()=prefs.getString("report",null);fun summary()=AutoAdaptSummary(prefs.getLong("time",0),prefs.getInt("packages",0),prefs.getInt("matched",0),prefs.getInt("blocked",0)) }
data class AutoAdaptSummary(val timestamp:Long=0,val packageCount:Int=0,val matchedCount:Int=0,val blockedCount:Int=0)

object AutoAdaptScheduler { const val UNIQUE_WORK="auto-adapt-dry-run";fun enqueue(context:Context){WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK,ExistingWorkPolicy.REPLACE,OneTimeWorkRequestBuilder<AutoAdaptDryRunWorker>().setInitialDelay(7,TimeUnit.SECONDS).build())} }

/** Phase 1 reads private baseline and package resources only. It has no Root executor or theme mutation dependency. */
class AutoAdaptDryRunWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result { val pending=PendingPackageStore(applicationContext);val snapshot=pending.snapshot();if(snapshot.isEmpty())return Result.success();val result=AutoAdaptDryRunEngine(applicationContext).run(snapshot);AutoAdaptDryRunStore(applicationContext).save(result);pending.clearProcessed(snapshot);if(pending.snapshot().isNotEmpty())AutoAdaptScheduler.enqueue(applicationContext);return Result.success() }
}
class AutoAdaptDryRunEngine(private val context:Context) {
    fun run(events:List<PendingPackageEvent>):AutoAdaptDryRunResult { val mode=ActiveThemeModeStore(context).get();val baseline=FileThemeSessionStore(File(context.filesDir,"theme-session.txt")).load()?.backup?.takeIf{it.isFile};val index=baseline?.let(HyperOsThemeArchiveIndex::from);val repo=InstalledAppsRepository(context);val pack=if(mode==ActiveThemeMode.ICON_PACK)SelectedIconPackStore(context).selectedApk()?.let{runCatching{IconPackParser(context).parseApk(it)}.getOrNull()}else null
        return AutoAdaptDryRunResult(System.currentTimeMillis(),events.map{event->val apps=repo.launcherAppsForPackage(event.packageName);when{mode==ActiveThemeMode.NONE->AutoAdaptPackageResult(event.packageName,event.eventType,mode,apps.size,AutoAdaptSourceStatus.NO_ACTIVE_MODE,emptyList(),0,"NO_ACTIVE_APPLIED_MODE");apps.isEmpty()->AutoAdaptPackageResult(event.packageName,event.eventType,mode,0,AutoAdaptSourceStatus.NOT_CHECKED,emptyList(),0,"NO_LAUNCHER_COMPONENT");baseline==null->AutoAdaptPackageResult(event.packageName,event.eventType,mode,apps.size,AutoAdaptSourceStatus.NOT_CHECKED,emptyList(),0,"NO_MANAGED_THEME_BASELINE");mode==ActiveThemeMode.ICON_PACK&&pack==null->AutoAdaptPackageResult(event.packageName,event.eventType,mode,apps.size,AutoAdaptSourceStatus.SOURCE_UNAVAILABLE,emptyList(),0,"ICON_PACK_SOURCE_UNAVAILABLE");else->{val routes=apps.map{app->HyperOsThemeEntryResolver.resolve(index!!,com.wikiglobal.iconconverter.hyperos.LauncherComponentIdentity.from(app.packageName,app.launcherActivity,app.targetActivity,app.activityAliases)).toAutoPlan()};val status=when(mode){ActiveThemeMode.MATERIAL_YOU->AutoAdaptSourceStatus.MATERIAL_RENDER_CHECK_DEFERRED;ActiveThemeMode.ICON_PACK->{val matches=IconMatcher.match(apps,pack!!.mappings,pack.calendars);when{matches.any{it.status==MatchStatus.CONFLICT}->AutoAdaptSourceStatus.ICON_PACK_CONFLICT;matches.any{it.status!=MatchStatus.UNMATCHED}->AutoAdaptSourceStatus.ICON_PACK_MATCHED;else->AutoAdaptSourceStatus.ICON_PACK_UNMATCHED}};else->AutoAdaptSourceStatus.NO_ACTIVE_MODE};AutoAdaptPackageResult(event.packageName,event.eventType,mode,apps.size,status,routes,routes.sumOf{it.wouldPatchEntries.size},"NONE")}}}) }
    private fun ComponentThemeRoute.toAutoPlan()=AutoAdaptRoutePlan(packageName,launcherActivity,targetActivity,packageEntry,currentActivityEntry,currentActivityEntryExisted,directMatchedEntries,targetFallbackMatchedEntries,legacyThemeAliasEntries,legacyAliasStatus.name,finalReplacementEntries)
}
object AutoAdaptReport { fun format(result:AutoAdaptDryRunResult,enabled:Boolean)=buildString{appendLine("AUTO_ADAPT_DRY_RUN_REPORT");appendLine("AUTO_ADAPT_ENABLED=$enabled");appendLine("DRY_RUN_ONLY=true");appendLine("ROOT_CALLED=false");appendLine("SYSTEM_THEME_MODIFIED=false");appendLine("PACKAGE_COUNT=${result.packages.size}");result.packages.forEach{p->appendLine();appendLine("PACKAGE=${p.packageName}");appendLine("EVENT=${p.eventType}");appendLine("ACTIVE_MODE=${p.activeMode}");appendLine("LAUNCHER_COMPONENTS=${p.launcherComponentCount}");appendLine("SOURCE_STATUS=${p.sourceStatus}");p.routes.forEach{r->appendLine("LAUNCHER_ACTIVITY=${r.launcherActivity}");appendLine("TARGET_ACTIVITY=${r.targetActivity?:"NONE"}");appendLine("PACKAGE_ENTRY=${r.packageEntry}");appendLine("CURRENT_ACTIVITY_ENTRY=${r.currentActivityEntry}");appendLine("CURRENT_ACTIVITY_ENTRY_EXISTED=${r.currentActivityEntryExisted}");appendLine("DIRECT_MATCHED_ENTRIES=${r.directMatchedEntries.joinToString(",")}");appendLine("TARGET_FALLBACK_MATCHED_ENTRIES=${r.targetFallbackMatchedEntries.joinToString(",")}");appendLine("LEGACY_THEME_ALIAS_ENTRIES=${r.legacyThemeAliasEntries.joinToString(",")}");appendLine("LEGACY_ALIAS_STATUS=${r.legacyAliasStatus}");appendLine("WOULD_PATCH_ENTRIES=${r.wouldPatchEntries.joinToString(",")}")};appendLine("BLOCK_REASON=${p.blockReason}")}} }
