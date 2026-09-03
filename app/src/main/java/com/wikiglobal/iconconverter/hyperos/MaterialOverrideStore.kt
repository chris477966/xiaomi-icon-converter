package com.wikiglobal.iconconverter.hyperos

import android.content.Context

enum class MaterialSourceOverride { AUTO, NATIVE, LAWNICONS, AOSP_FORCE, KEEP }
class MaterialOverrideStore(context: Context) { private val prefs=context.getSharedPreferences("material-source-overrides",Context.MODE_PRIVATE); fun get(key:String)=runCatching{MaterialSourceOverride.valueOf(prefs.getString(key,"AUTO")!!)}.getOrDefault(MaterialSourceOverride.AUTO); fun set(key:String,value:MaterialSourceOverride){prefs.edit().putString(key,value.name).apply()} }
