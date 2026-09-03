package com.wikiglobal.iconconverter.hyperos

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable

data class BackgroundSegmentationConfig(val colorDistance: Float = .16f, val minimumComponentPixels: Int = 24)
data class GeneratedMaskResult(val mask: Bitmap?, val analysis: GlyphAnalysis?, val rejected: MonetGlyphSource?)

/** Conservative edge-connected background removal. Pixels matching the border colour are removed only when reachable from an edge. */
object GeneratedGlyphExtractor {
    fun extract(drawable: Drawable, config: BackgroundSegmentationConfig = BackgroundSegmentationConfig(), safety: GlyphSafetyConfig = GlyphSafetyConfig()): GeneratedMaskResult {
        val size = 256; val source = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888); val old = Rect(drawable.bounds)
        drawable.setBounds(0, 0, size, size); drawable.draw(Canvas(source)); drawable.bounds = old
        val pixels = IntArray(size * size); source.getPixels(pixels, 0, size, 0, 0, size, size)
        val alpha = extractAlpha(pixels, size, size, config); val analysis = GlyphSafetyAnalyzer.analyze(alpha, size, size, safety); val rejected = GlyphSafetyAnalyzer.reject(analysis, safety)
        if (rejected != null) return GeneratedMaskResult(null, analysis, rejected)
        val mask = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888); val out = IntArray(alpha.size) { alpha[it] shl 24 }; mask.setPixels(out, 0, size, 0, 0, size, size)
        return GeneratedMaskResult(mask, analysis, null)
    }
    /** Pure implementation used by unit tests. */
    fun extractAlpha(pixels: IntArray, width: Int, height: Int, config: BackgroundSegmentationConfig = BackgroundSegmentationConfig()): IntArray {
        val border = mutableListOf<Int>(); fun add(x: Int, y: Int) { border += pixels[y * width + x] }
        for (x in 0 until width) { add(x, 0); add(x, height - 1) }; for (y in 1 until height - 1) { add(0, y); add(width - 1, y) }
        val bg = border.filter { it ushr 24 > 20 }.groupBy { quantize(it) }.maxByOrNull { it.value.size }?.value?.firstOrNull() ?: 0
        val removed = BooleanArray(pixels.size); val queue = ArrayDeque<Int>()
        fun enqueue(i: Int) { if (!removed[i] && similar(pixels[i], bg, config.colorDistance)) { removed[i] = true; queue.add(i) } }
        for (x in 0 until width) { enqueue(x); enqueue((height - 1) * width + x) }; for (y in 1 until height - 1) { enqueue(y * width); enqueue(y * width + width - 1) }
        while (queue.isNotEmpty()) { val i = queue.removeFirst(); val x = i % width; val y = i / width; if (x > 0) enqueue(i - 1); if (x < width - 1) enqueue(i + 1); if (y > 0) enqueue(i - width); if (y < height - 1) enqueue(i + width) }
        val alpha = IntArray(pixels.size) { i -> if (removed[i]) 0 else pixels[i] ushr 24 }
        cleanup(alpha, width, height, config.minimumComponentPixels); return alpha
    }
    private fun cleanup(alpha: IntArray, w: Int, h: Int, minimum: Int) { val seen=BooleanArray(alpha.size); for (start in alpha.indices) if(alpha[start]>0&&!seen[start]) { val q=ArrayDeque<Int>();val members=mutableListOf<Int>();q.add(start);seen[start]=true; while(q.isNotEmpty()){val i=q.removeFirst();members+=i;val x=i%w;val y=i/w;listOf(i-1,i+1,i-w,i+w).filter { n -> n in alpha.indices && ((n/w==y)||(n%w==x)) && alpha[n]>0&&!seen[n] }.forEach{seen[it]=true;q.add(it)} }; if(members.size<minimum)members.forEach{alpha[it]=0} } }
    private fun quantize(c: Int) = ((c shr 20) and 0xf) shl 8 or ((c shr 12) and 0xf) shl 4 or ((c shr 4) and 0xf)
    private fun similar(a: Int, b: Int, threshold: Float): Boolean {
        if (a ushr 24 < 20) return true
        val ar = ((a shr 16) and 255) / 255f; val ag = ((a shr 8) and 255) / 255f; val ab = (a and 255) / 255f
        val br = ((b shr 16) and 255) / 255f; val bg = ((b shr 8) and 255) / 255f; val bb = (b and 255) / 255f
        val dr=ar-br; val dg=ag-bg; val db=ab-bb; val lum=abs((dr+dg+db)/3)
        return (dr*dr+dg*dg+db*db)/3f <= threshold*threshold && lum <= threshold
    }
    private fun abs(v: Float) = if (v < 0) -v else v
}
