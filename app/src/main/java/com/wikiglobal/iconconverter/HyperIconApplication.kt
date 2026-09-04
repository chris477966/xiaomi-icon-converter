package com.wikiglobal.iconconverter

import android.app.Application
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.wikiglobal.iconconverter.hyperos.WallpaperColorChangeNotifier
import com.wikiglobal.iconconverter.hyperos.WallpaperMonetScheduler

/** Honest best-effort detection: while this app process exists and whenever it returns foreground. */
class HyperIconApplication : Application(), DefaultLifecycleObserver {
    private val wallpaperReceiver=object:BroadcastReceiver(){override fun onReceive(context:android.content.Context,intent:Intent){if(Intent.ACTION_WALLPAPER_CHANGED==intent.action){WallpaperColorChangeNotifier.notifyChange();WallpaperMonetScheduler.enqueue(this@HyperIconApplication)}}}
    private val wallpaperColorsListener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) object : WallpaperManager.OnColorsChangedListener {
        override fun onColorsChanged(colors: android.app.WallpaperColors?, which: Int) {
            if (which and WallpaperManager.FLAG_SYSTEM != 0) WallpaperColorChangeNotifier.notifyChange()
        }
    } else null
    override fun onCreate(){super<Application>.onCreate();val filter=IntentFilter(Intent.ACTION_WALLPAPER_CHANGED);if(Build.VERSION.SDK_INT>=33)registerReceiver(wallpaperReceiver,filter,RECEIVER_NOT_EXPORTED)else @Suppress("DEPRECATION") registerReceiver(wallpaperReceiver,filter);if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) wallpaperColorsListener?.let { WallpaperManager.getInstance(this).addOnColorsChangedListener(it, Handler(Looper.getMainLooper())) };ProcessLifecycleOwner.get().lifecycle.addObserver(this)}
    override fun onStart(owner:LifecycleOwner){WallpaperMonetScheduler.enqueue(this)}
}
