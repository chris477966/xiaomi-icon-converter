package com.wikiglobal.iconconverter.hyperos

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import com.wikiglobal.iconconverter.model.InstalledApp
import com.wikiglobal.iconconverter.model.IconMapping
import com.wikiglobal.iconconverter.parser.IconPackParser
import java.io.File
import java.security.MessageDigest

enum class LawniconsMatchType { LAWNICONS_EXACT, LAWNICONS_PACKAGE_FALLBACK, LAWNICONS_ALIAS, NOT_FOUND }
data class LawniconsMatch(val drawable: Drawable? = null, val drawableName: String? = null, val type: LawniconsMatchType = LawniconsMatchType.NOT_FOUND)
/** Bundled Lawnicons remains an APK provider: no SVG database is copied into this project. */
class LawniconsProvider(private val context: Context) {
    private var pack: com.wikiglobal.iconconverter.model.IconPack? = null
    private var index: LawniconsIndex? = null
    fun load(): Boolean = runCatching {
        val target=File(context.cacheDir,"provider/lawnicons-2.18.0.apk"); target.parentFile?.mkdirs()
        if(!target.exists()||sha(target)!=SHA256) context.assets.open("providers/Lawnicons.2.18.0.apk").use{input->target.outputStream().use(input::copyTo)}
        check(sha(target)==SHA256){"Lawnicons integrity verification failed"}; pack=IconPackParser(context).parseApk(target); index=LawniconsIndex(pack!!.mappings); true
    }.getOrDefault(false)
    fun match(app: InstalledApp): LawniconsMatch { val p=pack ?: return LawniconsMatch(); val found=index?.match(app)?:return LawniconsMatch(); return found?.let{LawniconsMatch(p.drawableLoader(it.first),it.first,it.second)}?:LawniconsMatch() }
    fun alphaMask(drawable: Drawable): Bitmap? { val s=256; val b=Bitmap.createBitmap(s,s,Bitmap.Config.ARGB_8888);val old=Rect(drawable.bounds);drawable.setBounds(0,0,s,s);drawable.draw(Canvas(b));drawable.bounds=old;val p=IntArray(s*s);b.getPixels(p,0,s,0,0,s,s);p.indices.forEach{i->p[i]=(p[i] ushr 24) shl 24};val visible=p.count{(it ushr 24)>0};return if(visible in 16 until p.size) b.also{it.setPixels(p,0,s,0,0,s,s)} else null }
    private fun sha(file:File)=file.inputStream().use{MessageDigest.getInstance("SHA-256").digest(it.readBytes()).joinToString(""){b->"%02x".format(b)}}
    companion object { const val SHA256="e830b37e1cd7cd66487492f4a1084ba086254b2b09541d729da0ec2169a73bfe" }
}
/** Pre-indexes official appfilter mappings; package fallback is rejected when it would be ambiguous. */
class LawniconsIndex(mappings:List<IconMapping>, private val aliases:Map<String,String> = emptyMap()) {
    private val exact=mappings.filter{it.component.activityName!=null}.associateBy{it.component.packageName+"#"+it.component.activityName}
    private val packages=mappings.groupBy{it.component.packageName}.mapValues{(_,v)->v.map{it.drawableName}.distinct()}
    fun match(app:InstalledApp):Pair<String,LawniconsMatchType>? { val key=app.packageName+"#"+app.launcherActivity;exact[key]?.let{return it.drawableName to LawniconsMatchType.LAWNICONS_EXACT}; aliases[key]?.let{alias->exact[app.packageName+"#"+alias]?.let{return it.drawableName to LawniconsMatchType.LAWNICONS_ALIAS}}; val candidates=packages[app.packageName]?:return null;return if(candidates.size==1)candidates.single() to LawniconsMatchType.LAWNICONS_PACKAGE_FALLBACK else null }
}
