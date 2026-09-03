package com.wikiglobal.iconconverter.hyperos

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable

/** Apache-2.0 derived implementation of AOSP MonochromeIconFactory's adaptive-icon luminance/contrast pipeline. */
object AospMonochromeGenerator {
    fun generate(icon: AdaptiveIconDrawable): MonetGlyphResult = runCatching {
        val size=256; val flat=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888); val canvas=Canvas(flat); icon.background.setBounds(0,0,size,size);icon.background.draw(canvas);icon.foreground.setBounds(0,0,size,size);icon.foreground.draw(canvas)
        val pixels=IntArray(size*size);flat.getPixels(pixels,0,size,0,0,size,size);val values=IntArray(pixels.size){i->val p=pixels[i];(((p shr 16)and 255)+((p shr 8)and 255)+(p and 255))/3};val min=values.min();val max=values.max();if(min==max) return@runCatching MonetGlyphResult(null,MonetGlyphSource.UNAVAILABLE_EMPTY,null,MonetGlyphSource.UNAVAILABLE_EMPTY)
        val alpha=IntArray(values.size){i->(values[i]-min)*255/(max-min)}; val edge=(alpha.take(size).average()+alpha.takeLast(size).average())/510.0; if(edge>.5)alpha.indices.forEach{i->alpha[i]=255-alpha[i]}; val mask=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);mask.setPixels(IntArray(alpha.size){alpha[it] shl 24},0,size,0,0,size,size); val analysis=GlyphSafetyAnalyzer.analyze(alpha,size,size);MonetGlyphResult(mask,MonetGlyphSource.AOSP_FORCED_MONOCHROME,analysis)
    }.getOrElse{MonetGlyphResult(null,MonetGlyphSource.UNAVAILABLE_EMPTY,null,MonetGlyphSource.UNAVAILABLE_EMPTY)}
}
