package com.wikiglobal.iconconverter.hyperos

/** Ordered selection helper shared by native-monochrome and AOSP-adaptive discovery. */
object MaterialCandidateDiscovery {
    /** Keeps checking later candidates when an earlier launcher presentation is legacy/bitmap. */
    fun <T> firstAvailable(candidates: List<Pair<RawIconSource, T?>>): Pair<RawIconSource, T>? =
        candidates.firstOrNull { it.second != null }?.let { it.first to it.second!! }
}
