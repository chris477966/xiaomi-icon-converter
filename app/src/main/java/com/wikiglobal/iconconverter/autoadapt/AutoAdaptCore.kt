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
import com.wikiglobal.iconconverter.hyperos.HyperOs3ThemePatcher
import com.wikiglobal.iconconverter.hyperos.HyperOsThemeArchiveIndex
import com.wikiglobal.iconconverter.hyperos.HyperOsThemeEntryResolver
import com.wikiglobal.iconconverter.hyperos.LauncherComponentIdentity
import com.wikiglobal.iconconverter.matcher.IconMatcher
import com.wikiglobal.iconconverter.model.IconPack
import com.wikiglobal.iconconverter.model.InstalledApp
import com.wikiglobal.iconconverter.model.MatchStatus
import com.wikiglobal.iconconverter.parser.IconPackParser
import com.wikiglobal.iconconverter.repository.InstalledAppsRepository
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

enum class AutoAdaptEventType { NEW_INSTALL, REPLACED }
enum class ActiveThemeMode { NONE, ICON_PACK, MATERIAL_YOU }
enum class AutoAdaptSourceStatus { NOT_CHECKED, ICON_PACK_MATCHED, ICON_PACK_UNMATCHED, ICON_PACK_CONFLICT, MATERIAL_RENDER_CHECK_DEFERRED, NO_ACTIVE_MODE, SOURCE_UNAVAILABLE }
data class PendingPackageEvent(val packageName:String,val eventType:AutoAdaptEventType,val timestamp:Long)
data class AutoAdaptRoutePlan(val packageName:String,val launcherActivity:String,val targetActivity:String?,val packageEntry:String,val currentActivityEntry:String,val currentActivityEntryExisted:Boolean,val directMatchedEntries:List<String>,val targetFallbackMatchedEntries:List<String>,val legacyThemeAliasEntries:List<String>,val legacyAliasStatus:String,val sourceStatus:AutoAdaptSourceStatus,val wouldPatchEntries:List<String>)
data class AutoAdaptPackageResult(val packageName:String,val eventType:AutoAdaptEventType,val activeMode:ActiveThemeMode,val launcherComponentCount:Int,val sourceStatus:AutoAdaptSourceStatus,val matchedComponents:Int,val unmatchedComponents:Int,val conflictComponents:Int,val routes:List<AutoAdaptRoutePlan>,val wouldPatchEntryCount:Int,val blockReason:String)
data class AutoAdaptDryRunResult(val timestamp:Long,val packages:List<AutoAdaptPackageResult>)

interface AutoAdaptWorkScheduler { fun enqueue(); fun cancel() }
interface AutoAdaptSettings { fun enabled():Boolean }
interface PendingPackageEvents { fun record(packageName:String,eventType:AutoAdaptEventType):PendingPackageEvent; fun snapshot():List<PendingPackageEvent>; fun clearProcessed(snapshot:List<PendingPackageEvent>); fun clear() }
class WorkManagerAutoAdaptScheduler(private val context:Context):AutoAdaptWorkScheduler {
    override fun enqueue(){WorkManager.getInstance(context).enqueueUniqueWork(AUTO_ADAPT_DRY_RUN,ExistingWorkPolicy.REPLACE,OneTimeWorkRequestBuilder<AutoAdaptDryRunWorker>().setInitialDelay(7,TimeUnit.SECONDS).build())}
    override fun cancel(){WorkManager.getInstance(context).cancelUniqueWork(AUTO_ADAPT_DRY_RUN)}
}
const val AUTO_ADAPT_DRY_RUN="auto-adapt-dry-run"
class AutoAdaptSettingsStore(context:Context) : AutoAdaptSettings { private val prefs=context.getSharedPreferences("auto-adapt-settings",Context.MODE_PRIVATE); override fun enabled()=prefs.getBoolean("enabled",false); fun setEnabled(enabled:Boolean){prefs.edit().putBoolean("enabled",enabled).apply()} }
class ActiveThemeModeStore(context:Context) { private val prefs=context.getSharedPreferences("active-theme-mode",Context.MODE_PRIVATE); fun get()=runCatching{ActiveThemeMode.valueOf(prefs.getString("mode",ActiveThemeMode.NONE.name)!!)}.getOrDefault(ActiveThemeMode.NONE); fun set(mode:ActiveThemeMode){prefs.edit().putString("mode",mode.name).apply()} }
class PendingPackageStore(context:Context) : PendingPackageEvents {
    private val prefs=context.getSharedPreferences("auto-adapt-pending",Context.MODE_PRIVATE)
    override fun snapshot():List<PendingPackageEvent> = prefs.all.mapNotNull { (pkg,value) -> (value as? String)?.split('|')?.takeIf{it.size==2}?.let { parts -> runCatching { PendingPackageEvent(pkg,AutoAdaptEventType.valueOf(parts[0]),parts[1].toLong()) }.getOrNull() } }.sortedBy { it.packageName }
    override fun record(packageName:String,eventType:AutoAdaptEventType):PendingPackageEvent { val old=snapshot().firstOrNull{it.packageName==packageName};val type=if(eventType==AutoAdaptEventType.REPLACED||old?.eventType==AutoAdaptEventType.REPLACED)AutoAdaptEventType.REPLACED else AutoAdaptEventType.NEW_INSTALL;val next=PendingPackageEvent(packageName,type,maxOf(System.currentTimeMillis(),(old?.timestamp?:0)+1));prefs.edit().putString(packageName,"${next.eventType.name}|${next.timestamp}").commit();return next }
    override fun clearProcessed(snapshot:List<PendingPackageEvent>) { val current=this.snapshot().associateBy{it.packageName};val edit=prefs.edit();snapshot.forEach{event->if(current[event.packageName]?.timestamp==event.timestamp)edit.remove(event.packageName)};edit.commit() }
    override fun clear(){prefs.edit().clear().commit()}
}

data class SelectedIconPackMetadata(val packageName:String,val displayName:String,val sha256:String,val importedAt:Long)
sealed class SelectedIconPackValidation { data class Valid(val file:File,val metadata:SelectedIconPackMetadata):SelectedIconPackValidation(); data object Missing:SelectedIconPackValidation(); data object ShaMismatch:SelectedIconPackValidation() }
interface IconPackFileMover { fun move(source:File,target:File,atomic:Boolean) }
object NioIconPackFileMover:IconPackFileMover { override fun move(source:File,target:File,atomic:Boolean){val options=if(atomic)arrayOf(StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)else arrayOf(StandardCopyOption.REPLACE_EXISTING);Files.move(source.toPath(),target.toPath(),*options)} }
class SelectedIconPackStore(private val context:Context,private val mover:IconPackFileMover=NioIconPackFileMover) {
    private val dir=File(context.filesDir,"icon-pack");private val apk get()=File(dir,"selected.apk");private val previous get()=File(dir,"selected.apk.prev");private val prefs=context.getSharedPreferences("selected-icon-pack",Context.MODE_PRIVATE)
    fun copyToTemporary(uri:Uri):File { dir.mkdirs();val temp=File(dir,"selected.apk.tmp");context.contentResolver.openInputStream(uri)?.use{input->FileOutputStream(temp).use{output->input.copyTo(output);output.fd.sync()}}?:error("无法读取所选 APK");return temp }
    /** Never deletes the only known-good source before a same-directory replacement succeeds. */
    fun commitTemporary(temp:File,metadata:SelectedIconPackMetadata):File { check(temp.parentFile==dir&&temp.isFile){"临时图标包无效"};check(HyperOs3ThemePatcher.sha256(temp)==metadata.sha256){"临时图标包校验失败"};dir.mkdirs();try{mover.move(temp,apk,true)}catch(_:AtomicMoveNotSupportedException){replaceWithRollback(temp)};check(prefs.edit().putString("package",metadata.packageName).putString("label",metadata.displayName).putString("sha",metadata.sha256).putLong("imported",metadata.importedAt).commit()){"图标包元数据保存失败"};return apk }
    private fun replaceWithRollback(temp:File){if(previous.exists())previous.delete();if(apk.exists())mover.move(apk,previous,false);try{mover.move(temp,apk,false)}catch(error:Throwable){if(previous.exists())mover.move(previous,apk,false);throw error};previous.delete()}
    fun selectedApk():File?=apk.takeIf{it.isFile}
    fun metadata():SelectedIconPackMetadata?=if(!apk.isFile||!prefs.contains("sha"))null else SelectedIconPackMetadata(prefs.getString("package","")!!,prefs.getString("label","")!!,prefs.getString("sha","")!!,prefs.getLong("imported",0))
    fun validateSelected():SelectedIconPackValidation { val file=selectedApk()?:return SelectedIconPackValidation.Missing;val meta=metadata()?:return SelectedIconPackValidation.Missing;return if(HyperOs3ThemePatcher.sha256(file)==meta.sha256)SelectedIconPackValidation.Valid(file,meta)else SelectedIconPackValidation.ShaMismatch }
}

data class AutoAdaptSummary(val timestamp:Long=0,val packageCount:Int=0,val matchedCount:Int=0,val blockedCount:Int=0)
interface AutoAdaptResultSink { fun save(result:AutoAdaptDryRunResult,enabled:Boolean) }
class AutoAdaptDryRunStore(context:Context) : AutoAdaptResultSink { private val prefs=context.getSharedPreferences("auto-adapt-result",Context.MODE_PRIVATE);override fun save(result:AutoAdaptDryRunResult,enabled:Boolean){prefs.edit().putString("report",AutoAdaptReport.format(result,enabled)).putLong("time",result.timestamp).putInt("packages",result.packages.size).putInt("matched",result.packages.sumOf{it.matchedComponents}).putInt("blocked",result.packages.count{it.blockReason!="NONE"}).commit()};fun report()=prefs.getString("report",null);fun summary()=AutoAdaptSummary(prefs.getLong("time",0),prefs.getInt("packages",0),prefs.getInt("matched",0),prefs.getInt("blocked",0)) }

class PackageEventHandler(private val ownPackageName:String,private val settings:AutoAdaptSettings,private val pending:PendingPackageEvents,private val scheduler:AutoAdaptWorkScheduler) {
    fun handle(action:String?,packageName:String?,replacing:Boolean){if(!settings.enabled()||packageName.isNullOrBlank()||packageName==ownPackageName)return;val type=when(action){android.content.Intent.ACTION_PACKAGE_ADDED->if(replacing)AutoAdaptEventType.REPLACED else AutoAdaptEventType.NEW_INSTALL;android.content.Intent.ACTION_PACKAGE_REPLACED->AutoAdaptEventType.REPLACED;else->return};pending.record(packageName,type);scheduler.enqueue()}
}
class AutoAdaptDisableController(private val pending:PendingPackageEvents,private val scheduler:AutoAdaptWorkScheduler) { fun disable(){scheduler.cancel();pending.clear()} }

sealed class IconPackReadResult { data class Available(val pack:IconPack):IconPackReadResult(); data object Missing:IconPackReadResult(); data object ShaMismatch:IconPackReadResult(); data object Invalid:IconPackReadResult() }
interface AutoAdaptReadOnlyDependencies { fun activeMode():ActiveThemeMode; fun baseline():File?; fun launcherApps(packageName:String):List<InstalledApp>; fun selectedIconPack():IconPackReadResult }
class AndroidAutoAdaptReadOnlyDependencies(private val context:Context):AutoAdaptReadOnlyDependencies {
    override fun activeMode()=ActiveThemeModeStore(context).get()
    override fun baseline()=FileThemeSessionStore(File(context.filesDir,"theme-session.txt")).load()?.backup?.takeIf{it.isFile}
    override fun launcherApps(packageName:String)=InstalledAppsRepository(context).launcherAppsForPackage(packageName)
    override fun selectedIconPack():IconPackReadResult=when(val selected=SelectedIconPackStore(context).validateSelected()){SelectedIconPackValidation.Missing->IconPackReadResult.Missing;SelectedIconPackValidation.ShaMismatch->IconPackReadResult.ShaMismatch;is SelectedIconPackValidation.Valid->runCatching{IconPackParser(context).parseApk(selected.file)}.fold({IconPackReadResult.Available(it)},{IconPackReadResult.Invalid})}
}
/** Strictly read-only dry-run engine. Its dependency interface deliberately has no Root or theme-install operation. */
class AutoAdaptDryRunEngine(private val dependencies:AutoAdaptReadOnlyDependencies) {
    fun run(events:List<PendingPackageEvent>):AutoAdaptDryRunResult { val mode=dependencies.activeMode();val baseline=dependencies.baseline();val index=baseline?.let(HyperOsThemeArchiveIndex::from);val source=if(mode==ActiveThemeMode.ICON_PACK)dependencies.selectedIconPack()else null;return AutoAdaptDryRunResult(System.currentTimeMillis(),events.map{event->plan(event,mode,index,source)}) }
    private fun plan(event:PendingPackageEvent,mode:ActiveThemeMode,index:HyperOsThemeArchiveIndex?,source:IconPackReadResult?):AutoAdaptPackageResult { val apps=dependencies.launcherApps(event.packageName);if(mode==ActiveThemeMode.NONE)return blocked(event,mode,apps,"NO_ACTIVE_APPLIED_MODE",AutoAdaptSourceStatus.NO_ACTIVE_MODE);if(apps.isEmpty())return blocked(event,mode,apps,"NO_LAUNCHER_COMPONENT",AutoAdaptSourceStatus.NOT_CHECKED);if(index==null)return blocked(event,mode,apps,"NO_MANAGED_THEME_BASELINE",AutoAdaptSourceStatus.NOT_CHECKED);if(mode==ActiveThemeMode.ICON_PACK&&source !is IconPackReadResult.Available){val reason=when(source){IconPackReadResult.ShaMismatch->"ICON_PACK_SOURCE_SHA_MISMATCH";IconPackReadResult.Invalid->"ICON_PACK_SOURCE_INVALID";else->"ICON_PACK_SOURCE_UNAVAILABLE"};return blocked(event,mode,apps,reason,AutoAdaptSourceStatus.SOURCE_UNAVAILABLE)}
        val matchByKey=if(source is IconPackReadResult.Available)IconMatcher.match(apps,source.pack.mappings,source.pack.calendars).associateBy{it.app.packageName+"#"+it.app.launcherActivity}else emptyMap();val routes=apps.map{app->val status=when(mode){ActiveThemeMode.ICON_PACK->when(matchByKey[app.packageName+"#"+app.launcherActivity]?.status){MatchStatus.CONFLICT->AutoAdaptSourceStatus.ICON_PACK_CONFLICT;MatchStatus.UNMATCHED,null->AutoAdaptSourceStatus.ICON_PACK_UNMATCHED;else->AutoAdaptSourceStatus.ICON_PACK_MATCHED};ActiveThemeMode.MATERIAL_YOU->AutoAdaptSourceStatus.MATERIAL_RENDER_CHECK_DEFERRED;else->AutoAdaptSourceStatus.NO_ACTIVE_MODE};HyperOsThemeEntryResolver.resolve(index,LauncherComponentIdentity.from(app.packageName,app.launcherActivity,app.targetActivity,app.activityAliases)).toAutoPlan(status,mode==ActiveThemeMode.ICON_PACK&&status==AutoAdaptSourceStatus.ICON_PACK_MATCHED)};val matched=routes.count{it.sourceStatus==AutoAdaptSourceStatus.ICON_PACK_MATCHED};val unmatched=routes.count{it.sourceStatus==AutoAdaptSourceStatus.ICON_PACK_UNMATCHED};val conflicts=routes.count{it.sourceStatus==AutoAdaptSourceStatus.ICON_PACK_CONFLICT};val aggregate=when(mode){ActiveThemeMode.MATERIAL_YOU->AutoAdaptSourceStatus.MATERIAL_RENDER_CHECK_DEFERRED;ActiveThemeMode.ICON_PACK->when{conflicts>0->AutoAdaptSourceStatus.ICON_PACK_CONFLICT;matched>0->AutoAdaptSourceStatus.ICON_PACK_MATCHED;else->AutoAdaptSourceStatus.ICON_PACK_UNMATCHED};else->AutoAdaptSourceStatus.NO_ACTIVE_MODE};return AutoAdaptPackageResult(event.packageName,event.eventType,mode,apps.size,aggregate,matched,unmatched,conflicts,routes,routes.flatMap{it.wouldPatchEntries}.distinct().size,"NONE") }
    private fun blocked(event:PendingPackageEvent,mode:ActiveThemeMode,apps:List<InstalledApp>,reason:String,status:AutoAdaptSourceStatus)=AutoAdaptPackageResult(event.packageName,event.eventType,mode,apps.size,status,0,0,0,emptyList(),0,reason)
    private fun ComponentThemeRoute.toAutoPlan(status:AutoAdaptSourceStatus,eligible:Boolean)=AutoAdaptRoutePlan(packageName,launcherActivity,targetActivity,packageEntry,currentActivityEntry,currentActivityEntryExisted,directMatchedEntries,targetFallbackMatchedEntries,legacyThemeAliasEntries,legacyAliasStatus.name,status,if(eligible)finalReplacementEntries else emptyList())
}

class AutoAdaptWorkerDrain(private val pending:PendingPackageEvents,private val resultSink:AutoAdaptResultSink,private val enabled:()->Boolean,private val runEngine:(List<PendingPackageEvent>)->AutoAdaptDryRunResult,private val scheduler:AutoAdaptWorkScheduler) { fun drain(snapshot:List<PendingPackageEvent>):Boolean = runCatching { resultSink.save(runEngine(snapshot),enabled());pending.clearProcessed(snapshot);if(pending.snapshot().isNotEmpty())scheduler.enqueue();true }.getOrDefault(false) }
class AutoAdaptDryRunWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result { val pending=PendingPackageStore(applicationContext);val snapshot=pending.snapshot();if(snapshot.isEmpty())return Result.success();val engine=AutoAdaptDryRunEngine(AndroidAutoAdaptReadOnlyDependencies(applicationContext));val ok=AutoAdaptWorkerDrain(pending,AutoAdaptDryRunStore(applicationContext),AutoAdaptSettingsStore(applicationContext)::enabled,engine::run,WorkManagerAutoAdaptScheduler(applicationContext)).drain(snapshot);return if(ok)Result.success() else Result.retry() }
}
object AutoAdaptReport { fun format(result:AutoAdaptDryRunResult,enabled:Boolean)=buildString{appendLine("AUTO_ADAPT_DRY_RUN_REPORT");appendLine("AUTO_ADAPT_ENABLED=$enabled");appendLine("DRY_RUN_ONLY=true");appendLine("ROOT_PATH_AVAILABLE=false");appendLine("SYSTEM_THEME_MODIFIED=false");appendLine("PACKAGE_COUNT=${result.packages.size}");result.packages.forEach{p->appendLine();appendLine("PACKAGE=${p.packageName}");appendLine("EVENT=${p.eventType}");appendLine("ACTIVE_MODE=${p.activeMode}");appendLine("LAUNCHER_COMPONENTS=${p.launcherComponentCount}");appendLine("SOURCE_STATUS=${p.sourceStatus}");appendLine("MATCHED_COMPONENTS=${p.matchedComponents}");appendLine("UNMATCHED_COMPONENTS=${p.unmatchedComponents}");appendLine("CONFLICT_COMPONENTS=${p.conflictComponents}");p.routes.forEach{r->appendLine("LAUNCHER_ACTIVITY=${r.launcherActivity}");appendLine("TARGET_ACTIVITY=${r.targetActivity?:"NONE"}");appendLine("SOURCE_STATUS=${r.sourceStatus}");appendLine("SOURCE_ELIGIBILITY=${if(r.sourceStatus==AutoAdaptSourceStatus.MATERIAL_RENDER_CHECK_DEFERRED)"DEFERRED_PHASE_2" else r.sourceStatus}");appendLine("PACKAGE_ENTRY=${r.packageEntry}");appendLine("CURRENT_ACTIVITY_ENTRY=${r.currentActivityEntry}");appendLine("CURRENT_ACTIVITY_ENTRY_EXISTED=${r.currentActivityEntryExisted}");appendLine("DIRECT_MATCHED_ENTRIES=${r.directMatchedEntries.joinToString(",")}");appendLine("TARGET_FALLBACK_MATCHED_ENTRIES=${r.targetFallbackMatchedEntries.joinToString(",")}");appendLine("LEGACY_THEME_ALIAS_ENTRIES=${r.legacyThemeAliasEntries.joinToString(",")}");appendLine("LEGACY_ALIAS_STATUS=${r.legacyAliasStatus}");appendLine("WOULD_PATCH_ENTRIES=${r.wouldPatchEntries.joinToString(",")}")};appendLine("WOULD_PATCH_ENTRY_COUNT=${p.wouldPatchEntryCount}");appendLine("BLOCK_REASON=${p.blockReason}")}} }
