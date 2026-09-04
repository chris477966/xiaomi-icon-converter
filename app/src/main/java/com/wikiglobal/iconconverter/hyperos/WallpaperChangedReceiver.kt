package com.wikiglobal.iconconverter.hyperos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Official wallpaper event entry point; scheduling performs no Root action in the receiver. */
class WallpaperChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_WALLPAPER_CHANGED == intent.action) WallpaperMonetScheduler.enqueue(context.applicationContext)
    }
}
