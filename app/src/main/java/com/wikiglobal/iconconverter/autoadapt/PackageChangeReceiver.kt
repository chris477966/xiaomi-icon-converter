package com.wikiglobal.iconconverter.autoadapt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PackageChangeReceiver:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){if(!AutoAdaptSettingsStore(context).enabled())return;val pkg=intent.data?.schemeSpecificPart?:return;if(pkg==context.packageName)return;val type=when(intent.action){Intent.ACTION_PACKAGE_ADDED->if(intent.getBooleanExtra(Intent.EXTRA_REPLACING,false))AutoAdaptEventType.REPLACED else AutoAdaptEventType.NEW_INSTALL;Intent.ACTION_PACKAGE_REPLACED->AutoAdaptEventType.REPLACED;else->return};PendingPackageStore(context).record(pkg,type);AutoAdaptScheduler.enqueue(context)}}
