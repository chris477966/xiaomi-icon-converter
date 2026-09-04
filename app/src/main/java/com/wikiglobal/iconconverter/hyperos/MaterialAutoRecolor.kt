package com.wikiglobal.iconconverter.hyperos

import android.content.Context
import android.graphics.BitmapFactory
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wikiglobal.iconconverter.compiler.XiaomiIconCompiler
import kotlinx.coroutines.delay
import java.io.File

enum class AutoRecolorStatus { IDLE, WAITING_FOR_PALETTE, NO_ACTION, UPDATED, AUTO_RECOLOR_BLOCKED_EXTERNAL_CHANGE, ROOT_UNAVAILABLE, CACHE_UNAVAILABLE, FAILED }
data class MaterialInstalledState(val isMaterialInstalled: Boolean = false, val colorMode: MaterialColorMode = MaterialColorMode.SYSTEM_MONET, val paletteHash: String = "", val customSeedColor: Int? = null, val shape: MaterialIconShape = MaterialIconShape.HYPEROS, val dark: Boolean = false, val generatedAt: Long = 0, val themeArchiveShaAfterInstall: String = "", val followWallpaperMonet: Boolean = false, val status: AutoRecolorStatus = AutoRecolorStatus.IDLE)
class MaterialInstalledStateStore(context: Context) {
    private val prefs=context.getSharedPreferences("material-installed-state",Context.MODE_PRIVATE)
    fun load()=MaterialInstalledState(prefs.getBoolean("installed",false),runCatching{MaterialColorMode.valueOf(prefs.getString("mode",MaterialColorMode.SYSTEM_MONET.name)!!)}.getOrDefault(MaterialColorMode.SYSTEM_MONET),prefs.getString("palette","")!!,prefs.takeIf{it.contains("seed")}?.getInt("seed",0),runCatching{MaterialIconShape.valueOf(prefs.getString("shape",MaterialIconShape.HYPEROS.name)!!)}.getOrDefault(MaterialIconShape.HYPEROS),prefs.getBoolean("dark",false),prefs.getLong("generated",0),prefs.getString("archive","")!!,prefs.getBoolean("follow",false),runCatching{AutoRecolorStatus.valueOf(prefs.getString("status",AutoRecolorStatus.IDLE.name)!!)}.getOrDefault(AutoRecolorStatus.IDLE))
    fun save(s:MaterialInstalledState)=prefs.edit().putBoolean("installed",s.isMaterialInstalled).putString("mode",s.colorMode.name).putString("palette",s.paletteHash).putInt("seed",s.customSeedColor?:0).putString("shape",s.shape.name).putBoolean("dark",s.dark).putLong("generated",s.generatedAt).putString("archive",s.themeArchiveShaAfterInstall).putBoolean("follow",s.followWallpaperMonet).putString("status",s.status.name).apply()
}
data class CachedGlyph(val key:String,val source:MonetGlyphSource,val file:File)
class MaterialGlyphCache(private val context:Context) {
    private val dir=File(context.filesDir,"material-glyph-cache")
    private val index=File(dir,"index.txt")
    fun save(key:String,result:MonetGlyphResult){val mask=result.alphaMask?:return;dir.mkdirs();val name=key.hashCode().toUInt().toString(16)+".png";val file=File(dir,name);file.outputStream().use{mask.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};val all=load().filterNot{it.key==key}+CachedGlyph(key,result.source,file);index.writeText(all.joinToString("\n"){"${it.key}\t${it.source}\t${it.file.name}"})}
    fun load(): List<CachedGlyph> = if(!index.isFile)emptyList()else index.readLines().mapNotNull{line->line.split('\t').takeIf{it.size==3}?.let{parts->runCatching{CachedGlyph(parts[0],MonetGlyphSource.valueOf(parts[1]),File(dir,parts[2]))}.getOrNull()}}.filter{it.file.isFile}
    fun mask(item:CachedGlyph)=BitmapFactory.decodeFile(item.file.path)
}

object WallpaperMonetScheduler {
    const val UNIQUE_WORK="hypericon-wallpaper-monet"
    fun enqueue(context:Context){WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK,ExistingWorkPolicy.REPLACE,OneTimeWorkRequestBuilder<WallpaperMonetWorker>().setInitialDelay(3,java.util.concurrent.TimeUnit.SECONDS).build())}
}

/** Wallpaper event worker: it never discovers a new provider source; it only recolors cached masks. */
class WallpaperMonetWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
    override suspend fun doWork():Result{
        val styleStore=MaterialStyleStore(applicationContext); val installedStore=MaterialInstalledStateStore(applicationContext); val installed=installedStore.load(); val style=styleStore.get()
        if(style.colorMode!=MaterialColorMode.SYSTEM_MONET||!style.followWallpaperMonet||!installed.isMaterialInstalled)return Result.success()
        var palette:MonetPalette?=null
        for (attempt in 0 until 5) { palette=MonetPaletteReader.read(); if(palette?.hash()!=installed.paletteHash) break; if(attempt<4) delay(3000) }
        val current=palette?:return Result.success()
        val root=SuThemeRootExecutor(); val cache=MaterialGlyphCache(applicationContext).load(); val rootAvailable=root.isRootAvailable()
        val currentThemeSha=if(rootAvailable)root.inspect(SuThemeRootExecutor.ACTIVE_ICONS)?.sha256 else null
        val decision=MaterialAutoRecolorCoordinator.decide(MaterialAutoRecolorInput(style,installed,current.hash(),currentThemeSha,rootAvailable,cache.isNotEmpty()))
        when(decision){
            MaterialAutoRecolorDecision.PALETTE_UNCHANGED->{installedStore.save(installed.copy(status=AutoRecolorStatus.NO_ACTION));return Result.success()}
            MaterialAutoRecolorDecision.ROOT_UNAVAILABLE->{installedStore.save(installed.copy(status=AutoRecolorStatus.ROOT_UNAVAILABLE));return Result.success()}
            MaterialAutoRecolorDecision.CACHE_UNAVAILABLE->{installedStore.save(installed.copy(status=AutoRecolorStatus.CACHE_UNAVAILABLE));return Result.success()}
            MaterialAutoRecolorDecision.EXTERNAL_THEME_CHANGED->{styleStore.set(style.copy(followWallpaperMonet=false));installedStore.save(installed.copy(status=AutoRecolorStatus.AUTO_RECOLOR_BLOCKED_EXTERNAL_CHANGE));return Result.success()}
            MaterialAutoRecolorDecision.DISABLED,MaterialAutoRecolorDecision.NO_MATERIAL_INSTALL->return Result.success()
            MaterialAutoRecolorDecision.RECOLOR->Unit
        }
        return runCatching{
            val glyphCache=MaterialGlyphCache(applicationContext);val replacements=cache.mapNotNull{item->glyphCache.mask(item)?.let{mask->MaterialStyledGlyphRenderer.render(MonetGlyphResult(mask,item.source,null),current,installed.dark,style.shape)?.let{png->item to png}}}.groupBy{it.first.key.substringBefore('#')}.flatMap{(pkg,values)->val diff=values.map{it.second.contentHashCode()}.distinct().size>1;values.flatMapIndexed{index,(item,png)->buildList{if(index==0)add(HyperOs3IconReplacement(pkg,png));if(diff)add(HyperOs3IconReplacement(pkg,png,XiaomiIconCompiler.activityPart(item.key.substringAfter('#'),pkg)))}}}.distinctBy{it.entryName}
            check(replacements.isNotEmpty());val base=File(applicationContext.filesDir,"staging/wallpaper-base-icons.zip").also{it.parentFile?.mkdirs()};check(root.copySystemFileTo(SuThemeRootExecutor.ACTIVE_ICONS,base).success);val patched=File(applicationContext.filesDir,"staging/wallpaper-patched-icons.zip");HyperOs3ThemePatcher.patch(base,patched,replacements);val manager=ThemeBackupManager(applicationContext.filesDir,root,FileThemeSessionStore(File(applicationContext.filesDir,"theme-session.txt")));val session=manager.install(patched,"system-monet","MATERIAL_YOU",current.hash()).getOrThrow();root.refreshIconCache();installedStore.save(installed.copy(paletteHash=current.hash(),themeArchiveShaAfterInstall=session.lastInstalledSha256.orEmpty(),generatedAt=System.currentTimeMillis(),status=AutoRecolorStatus.UPDATED));Result.success()
        }.getOrElse{installedStore.save(installed.copy(status=AutoRecolorStatus.FAILED));Result.success()}
    }
}
