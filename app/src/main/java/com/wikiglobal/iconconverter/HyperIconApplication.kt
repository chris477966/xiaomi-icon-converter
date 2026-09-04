package com.wikiglobal.iconconverter

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.wikiglobal.iconconverter.hyperos.SystemMonetChangeNotifier
import com.wikiglobal.iconconverter.hyperos.WallpaperMonetScheduler

/** Honest best-effort detection: while this app process exists and whenever it returns foreground. */
class HyperIconApplication : Application(), DefaultLifecycleObserver {
    private val wallpaperReceiver=object:BroadcastReceiver(){override fun onReceive(context:android.content.Context,intent:Intent){if(Intent.ACTION_WALLPAPER_CHANGED==intent.action){SystemMonetChangeNotifier.notifyChange();WallpaperMonetScheduler.enqueue(this@HyperIconApplication)}}}
    override fun onCreate(){super<Application>.onCreate();val filter=IntentFilter(Intent.ACTION_WALLPAPER_CHANGED);if(Build.VERSION.SDK_INT>=33)registerReceiver(wallpaperReceiver,filter,RECEIVER_NOT_EXPORTED)else @Suppress("DEPRECATION") registerReceiver(wallpaperReceiver,filter);ProcessLifecycleOwner.get().lifecycle.addObserver(this)}
    override fun onStart(owner:LifecycleOwner){WallpaperMonetScheduler.enqueue(this)}
}
