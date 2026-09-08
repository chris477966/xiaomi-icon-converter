package com.wikiglobal.iconconverter.autoadapt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PackageChangeReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        PackageEventHandler(context.packageName,AutoAdaptSettingsStore(context),PendingPackageStore(context),WorkManagerAutoAdaptScheduler(context))
            .handle(intent.action,intent.data?.schemeSpecificPart,intent.getBooleanExtra(Intent.EXTRA_REPLACING,false))
    }
}
