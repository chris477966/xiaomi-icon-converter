package com.wikiglobal.iconconverter.hyperos

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-local signal for the lightweight UI preview refresh.  It deliberately
 * carries no theme/root work: WallpaperMonetWorker remains the only automatic
 * system-theme apply path.
 */
object SystemMonetChangeNotifier {
    private val mutableChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes = mutableChanges.asSharedFlow()

    fun notifyChange() {
        mutableChanges.tryEmit(Unit)
    }
}

/** Semantic alias used by wallpaper producers; both names share one process flow. */
typealias WallpaperColorChangeNotifier = SystemMonetChangeNotifier
