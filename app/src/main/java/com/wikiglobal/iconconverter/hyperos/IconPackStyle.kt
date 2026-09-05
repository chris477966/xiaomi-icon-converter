package com.wikiglobal.iconconverter.hyperos

import android.content.Context

/** Kept independently from MaterialStyle: sharing geometry must not couple user choices. */
data class IconPackStyle(val shape: IconShape = IconShape.HYPEROS)
class IconPackStyleStore(context: Context) {
    private val prefs = context.getSharedPreferences("icon-pack-style", Context.MODE_PRIVATE)
    fun get() = IconPackStyle(runCatching { IconShape.valueOf(prefs.getString("shape", IconShape.HYPEROS.name)!!) }.getOrDefault(IconShape.HYPEROS))
    fun set(style: IconPackStyle) { prefs.edit().putString("shape", style.shape.name).apply() }
}
