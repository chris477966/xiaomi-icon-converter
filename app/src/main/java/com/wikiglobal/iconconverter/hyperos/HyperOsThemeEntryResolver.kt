package com.wikiglobal.iconconverter.hyperos

import java.io.File
import java.util.zip.ZipFile

enum class ActivityIdentityRole { DIRECT, TARGET_FALLBACK }
data class ActivityIdentity(val activity: String, val role: ActivityIdentityRole)
data class LauncherComponentIdentity(val packageName:String,val launcherActivity:String,val targetActivity:String?=null,val fallbackActivities:Set<String> = emptySet()) {
    val activities: List<ActivityIdentity> get() = buildList {
        add(ActivityIdentity(launcherActivity, ActivityIdentityRole.DIRECT))
        (fallbackActivities + listOfNotNull(targetActivity)).map { normalizeActivityClassName(packageName,it) }.filter { it != launcherActivity }.sorted().forEach { add(ActivityIdentity(it,ActivityIdentityRole.TARGET_FALLBACK)) }
    }
    companion object {
        fun from(packageName:String,launcherActivity:String,targetActivity:String?=null,fallbackActivities:Set<String> = emptySet()) = LauncherComponentIdentity(packageName,normalizeActivityClassName(packageName,launcherActivity),targetActivity?.let{normalizeActivityClassName(packageName,it)},fallbackActivities)
        fun from(packageName:String,launcherActivity:String,activityAliases:Set<String>) = from(packageName,launcherActivity,null,activityAliases)
    }
}
fun normalizeActivityClassName(packageName:String,activity:String):String { val v=activity.trim();return when {v.startsWith(".")->packageName+v;'.' !in v->"$packageName.$v";else->v} }
fun relativeActivity(packageName:String,fullActivity:String):String?=normalizeActivityClassName(packageName,fullActivity).takeIf{it.startsWith("$packageName.")}?.removePrefix("$packageName.")
fun simpleActivity(packageName:String,fullActivity:String)=normalizeActivityClassName(packageName,fullActivity).substringAfterLast('.')
class HyperOsThemeArchiveIndex private constructor(val entries:Set<String>){fun contains(n:String)=n in entries;companion object{fun from(a:File)=ZipFile(a).use{z->HyperOsThemeArchiveIndex(z.entries().asSequence().map{it.name}.filter{it.startsWith(HyperOs3ThemePatcher.DRAWABLE_PREFIX)&&it.endsWith(".png")}.toSet())}}}
data class ComponentThemeRoute(val packageName:String,val launcherActivity:String,val targetActivity:String?,val packageEntry:String,val directMatchedEntries:List<String>,val targetFallbackMatchedEntries:List<String>,val finalReplacementEntries:List<String>){val matchedActivityEntries get()=directMatchedEntries+targetFallbackMatchedEntries;val replacementEntries get()=finalReplacementEntries}
object HyperOsThemeEntryResolver {
 fun baseEntry(p:String)="${HyperOs3ThemePatcher.DRAWABLE_PREFIX}$p.png"
 private fun candidates(pkg:String,full:String):List<String>{val rel=relativeActivity(pkg,full);val simple=simpleActivity(pkg,full);return buildList{add("${HyperOs3ThemePatcher.DRAWABLE_PREFIX}$full.png");rel?.let{add("${HyperOs3ThemePatcher.DRAWABLE_PREFIX}$pkg#$it.png")};add("${HyperOs3ThemePatcher.DRAWABLE_PREFIX}$pkg#$simple.png");add("${HyperOs3ThemePatcher.DRAWABLE_PREFIX}$pkg.$simple.png")}}
 fun resolve(index:HyperOsThemeArchiveIndex,id:LauncherComponentIdentity):ComponentThemeRoute{val direct=id.activities.filter{it.role==ActivityIdentityRole.DIRECT}.flatMap{candidates(id.packageName,it.activity)}.distinct().filter(index::contains).sorted();val fallback=id.activities.filter{it.role==ActivityIdentityRole.TARGET_FALLBACK}.flatMap{candidates(id.packageName,it.activity)}.distinct().filter(index::contains).sorted();return ComponentThemeRoute(id.packageName,id.launcherActivity,id.targetActivity,baseEntry(id.packageName),direct,fallback,listOf(baseEntry(id.packageName))+direct+fallback)}
}
