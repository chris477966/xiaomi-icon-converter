package com.wikiglobal.iconconverter.hyperos

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import com.wikiglobal.iconconverter.renderer.IconRenderer
import java.security.MessageDigest

enum class MonetGlyphSource { NATIVE_MONOCHROME, SAFE_ADAPTIVE_FOREGROUND, SAFE_ICON_PACK_GLYPH, UNAVAILABLE_NO_SOURCE, UNAVAILABLE_EMPTY, UNAVAILABLE_FULL_BLEED, UNAVAILABLE_OPAQUE_EDGES, UNAVAILABLE_EXCESSIVE_COVERAGE }
data class GlyphSafetyConfig(val minimumAlphaCoverage: Float = .01f, val maximumAlphaCoverage: Float = .65f, val maximumEdgeAlphaCoverage: Float = .10f, val edgeInsetPx: Int = 6)
data class GlyphAnalysis(val alphaCoverage: Float, val opaqueCoverage: Float, val edgeAlphaCoverage: Float, val transparentBorderRatio: Float, val boundingBoxWidthRatio: Float, val boundingBoxHeightRatio: Float, val nonTransparentPixels: Int, val touchesAllEdges: Boolean)
data class MonetGlyphResult(val alphaMask: Bitmap?, val source: MonetGlyphSource, val analysis: GlyphAnalysis?, val rejectReason: MonetGlyphSource? = null)
object GlyphSafetyAnalyzer {
    fun analyze(alpha: IntArray, width: Int, height: Int, config: GlyphSafetyConfig = GlyphSafetyConfig()): GlyphAnalysis {
        var non=0; var opaque=0; var edge=0; var left=width; var top=height; var right=-1; var bottom=-1
        alpha.forEachIndexed { i,v -> if(v>0){ val x=i%width; val y=i/width; non++; if(v>=250)opaque++; if(x<config.edgeInsetPx||y<config.edgeInsetPx||x>=width-config.edgeInsetPx||y>=height-config.edgeInsetPx)edge++; left=minOf(left,x);right=maxOf(right,x);top=minOf(top,y);bottom=maxOf(bottom,y) } }
        val total=width*height; val bw=if(right<0)0 else right-left+1; val bh=if(bottom<0)0 else bottom-top+1
        return GlyphAnalysis(non.toFloat()/total,opaque.toFloat()/total,edge.toFloat()/total,1f-non.toFloat()/total,bw.toFloat()/width,bh.toFloat()/height,non,left==0&&top==0&&right==width-1&&bottom==height-1)
    }
    fun reject(a: GlyphAnalysis, c: GlyphSafetyConfig = GlyphSafetyConfig()): MonetGlyphSource? = when { a.nonTransparentPixels==0||a.alphaCoverage<c.minimumAlphaCoverage->MonetGlyphSource.UNAVAILABLE_EMPTY; a.alphaCoverage>c.maximumAlphaCoverage->MonetGlyphSource.UNAVAILABLE_EXCESSIVE_COVERAGE; a.touchesAllEdges->MonetGlyphSource.UNAVAILABLE_FULL_BLEED; a.edgeAlphaCoverage>c.maximumEdgeAlphaCoverage->MonetGlyphSource.UNAVAILABLE_OPAQUE_EDGES; else->null }
}
private object AlphaMaskRenderer {
    fun render(d: Drawable): Bitmap { val s=HyperOs3ThemePatcher.ICON_SIZE; val b=Bitmap.createBitmap(s,s,Bitmap.Config.ARGB_8888); val old=Rect(d.bounds); d.setBounds(0,0,s,s); d.draw(Canvas(b)); d.bounds=old; val p=IntArray(s*s);b.getPixels(p,0,s,0,0,s,s);p.indices.forEach{p[it]=(p[it] ushr 24) shl 24};b.setPixels(p,0,s,0,0,s,s);return b }
    fun analysis(b: Bitmap,c: GlyphSafetyConfig)=IntArray(b.width*b.height).also{b.getPixels(it,0,b.width,0,0,b.width,b.height);it.indices.forEach{i->it[i]=it[i] ushr 24}}.let{GlyphSafetyAnalyzer.analyze(it,b.width,b.height,c)}
}
data class MonetPalette(val accent100:Int,val accent200:Int,val accent700:Int,val accent800:Int,val accent2:Int?,val accent3:Int?){fun colors(dark:Boolean)=if(dark)accent700 to accent200 else accent100 to accent700;fun hash()=MessageDigest.getInstance("SHA-256").digest(listOf(accent100,accent200,accent700,accent800,accent2,accent3).joinToString().toByteArray()).joinToString(""){"%02x".format(it)}}
object MonetPaletteReader { fun read(r:Resources=Resources.getSystem()):MonetPalette?=runCatching{fun c(n:String):Int{val i=r.getIdentifier(n,"color","android");require(i!=0);return r.getColor(i,null)};MonetPalette(c("system_accent1_100"),c("system_accent1_200"),c("system_accent1_700"),c("system_accent1_800"),c("system_accent2_100"),c("system_accent3_100"))}.getOrNull() }
object MonochromeResolver {
    fun resolve(icon:Drawable?, pack:Drawable?=null, c:GlyphSafetyConfig=GlyphSafetyConfig()):MonetGlyphResult { val mono=(icon as? AdaptiveIconDrawable)?.takeIf{Build.VERSION.SDK_INT>=33}?.monochrome; if(mono!=null)return native(mono,c); if(icon is AdaptiveIconDrawable){val fg=candidate(icon.foreground,MonetGlyphSource.SAFE_ADAPTIVE_FOREGROUND,c);return if(fg.alphaMask!=null)fg else pack?.let{candidate(it,MonetGlyphSource.SAFE_ICON_PACK_GLYPH,c)}?:fg};return pack?.let{candidate(it,MonetGlyphSource.SAFE_ICON_PACK_GLYPH,c)}?:MonetGlyphResult(null,MonetGlyphSource.UNAVAILABLE_NO_SOURCE,null,MonetGlyphSource.UNAVAILABLE_NO_SOURCE) }
    private fun native(d:Drawable,c:GlyphSafetyConfig)=runCatching{val m=AlphaMaskRenderer.render(d);val a=AlphaMaskRenderer.analysis(m,c);if(a.nonTransparentPixels>0)MonetGlyphResult(m,MonetGlyphSource.NATIVE_MONOCHROME,a)else MonetGlyphResult(null,MonetGlyphSource.UNAVAILABLE_EMPTY,a,MonetGlyphSource.UNAVAILABLE_EMPTY)}.getOrElse{MonetGlyphResult(null,MonetGlyphSource.UNAVAILABLE_EMPTY,null,MonetGlyphSource.UNAVAILABLE_EMPTY)}
    private fun candidate(d:Drawable,s:MonetGlyphSource,c:GlyphSafetyConfig)=runCatching{val m=AlphaMaskRenderer.render(d);val a=AlphaMaskRenderer.analysis(m,c);val reject=GlyphSafetyAnalyzer.reject(a,c);if(reject==null)MonetGlyphResult(m,s,a)else MonetGlyphResult(null,reject,a,reject)}.getOrElse{MonetGlyphResult(null,MonetGlyphSource.UNAVAILABLE_EMPTY,null,MonetGlyphSource.UNAVAILABLE_EMPTY)}
}
object MonetGlyphRenderer { fun render(r:MonetGlyphResult,p:MonetPalette,dark:Boolean):ByteArray? { val m=r.alphaMask?:return null;val(bg,fg)=p.colors(dark);val s=HyperOs3ThemePatcher.ICON_SIZE;val b=Bitmap.createBitmap(s,s,Bitmap.Config.ARGB_8888);val c=Canvas(b);c.drawRoundRect(12f,12f,238f,238f,58f,58f,Paint(Paint.ANTI_ALIAS_FLAG).apply{color=bg});c.drawBitmap(m,null,Rect(42,42,208,208),Paint(Paint.ANTI_ALIAS_FLAG).apply{colorFilter=PorterDuffColorFilter(fg,PorterDuff.Mode.SRC_IN)});return if(MonetOutputValidator.validate(b,fg))IconRenderer.bitmapToPng(b)else null} }
object MonetOutputValidator { fun validate(b:Bitmap,fg:Int,c:GlyphSafetyConfig=GlyphSafetyConfig()):Boolean{if(b.width!=250||b.height!=250)return false;val p=IntArray(62500);b.getPixels(p,0,250,0,0,250,250);val n=p.count{(it ushr 24)>0&&(it and 0x00ffffff)==(fg and 0x00ffffff)};return n>0&&n.toFloat()/p.size<=c.maximumAlphaCoverage} }
