package com.wikiglobal.iconconverter.hyperos

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import kotlin.math.roundToInt

/** Apache-2.0 adaptation of AOSP MonochromeIconFactory (2024): viewport, edge inversion and two-phase contrast. */
object AospMonochromeGenerator {
    data class Viewport(val iconSize:Int, val bitmapSize:Int, val edgePixelLength:Int, val viewPortScale:Float)
    fun viewport(iconSize:Int, extraInset:Float):Viewport { val scale=1f/(1f+2f*extraInset); val bitmap=(iconSize*2f*scale).roundToInt(); return Viewport(iconSize,bitmap,((bitmap*(bitmap-iconSize))/2f).roundToInt().coerceAtLeast(0),scale) }
    fun transform(gray:IntArray, edgePixelLength:Int):IntArray? { if(gray.isEmpty())return null;val min=gray.min();val max=gray.max();if(min==max)return null;val mapped=IntArray(gray.size){i->((gray[i]-min)*255/(max-min)).coerceIn(0,255)}; val n=edgePixelLength.coerceAtMost(mapped.size/2); val edge=if(n==0)0.0 else (mapped.take(n).sum()+mapped.takeLast(n).sum()).toDouble()/(n*2*255.0); if(edge>.5)mapped.indices.forEach{i->mapped[i]=255-mapped[i]}; return IntArray(mapped.size){i->secondContrast(mapped[i])} }
    fun secondContrast(p:Int):Int { val x=p.coerceIn(0,255); val result=if(x>128){ val coefficient=1f-(x-128)/128f; (255f-coefficient*(255-x)).roundToInt() } else { val coefficient=1f-(128-x)/128f; (coefficient*x).roundToInt() }; return result.coerceIn(0,255) }
    fun generate(icon:AdaptiveIconDrawable):MonetGlyphResult=runCatching { val vp=viewport(HyperOs3ThemePatcher.ICON_SIZE,AdaptiveIconDrawable.getExtraInsetFraction());val b=Bitmap.createBitmap(vp.bitmapSize,vp.bitmapSize,Bitmap.Config.ARGB_8888);Canvas(b).drawColor(Color.BLACK);val left=(vp.bitmapSize-vp.iconSize)/2;icon.background.setBounds(left,left,left+vp.iconSize,left+vp.iconSize);icon.background.draw(Canvas(b));icon.foreground.setBounds(left,left,left+vp.iconSize,left+vp.iconSize);icon.foreground.draw(Canvas(b));val px=IntArray(vp.bitmapSize*vp.bitmapSize);b.getPixels(px,0,vp.bitmapSize,0,0,vp.bitmapSize,vp.bitmapSize);val gray=IntArray(px.size){i->val p=px[i];(((p shr 16)and 255)+((p shr 8)and 255)+(p and 255))/3};val alpha=transform(gray,vp.edgePixelLength)?:return@runCatching MonetGlyphResult(null,MonetGlyphSource.UNAVAILABLE_EMPTY,null,MonetGlyphSource.UNAVAILABLE_EMPTY);val mask=Bitmap.createBitmap(vp.bitmapSize,vp.bitmapSize,Bitmap.Config.ARGB_8888);mask.setPixels(IntArray(alpha.size){alpha[it] shl 24},0,vp.bitmapSize,0,0,vp.bitmapSize,vp.bitmapSize);MonetGlyphResult(mask,MonetGlyphSource.AOSP_FORCED_MONOCHROME,GlyphSafetyAnalyzer.analyze(alpha,vp.bitmapSize,vp.bitmapSize)) }.getOrElse{MonetGlyphResult(null,MonetGlyphSource.UNAVAILABLE_EMPTY,null,MonetGlyphSource.UNAVAILABLE_EMPTY)}
}
