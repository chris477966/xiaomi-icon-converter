package com.wikiglobal.iconconverter.hyperos

import android.os.Build

class HyperOsVersionDetector(private val shell: RootReadOnlyShell) {
    fun detect(): Map<String, String> {
        val names = listOf(
            "ro.mi.os.version.name",
            "ro.mi.os.version.incremental",
            "ro.miui.ui.version.name",
            "ro.build.version.release",
            "ro.build.version.sdk",
            "ro.product.device"
        )
        return names.associateWith { name ->
            val value = shell.command("getprop $name").output.trim()
            if (value.isBlank()) "NOT_FOUND" else value
        } + mapOf(
            "android.os.Build.VERSION.RELEASE" to (Build.VERSION.RELEASE ?: "NOT_FOUND"),
            "android.os.Build.VERSION.SDK_INT" to Build.VERSION.SDK_INT.toString(),
            "android.os.Build.DEVICE" to Build.DEVICE
        )
    }
}
