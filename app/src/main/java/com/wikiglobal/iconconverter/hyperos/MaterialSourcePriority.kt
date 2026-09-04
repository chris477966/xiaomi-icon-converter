package com.wikiglobal.iconconverter.hyperos

/** Official themed-icon precedence: platform native, curated Lawnicons, AOSP adaptive conversion, keep. */
object MaterialSourcePriority {
    fun <T> automatic(native: T?, lawnicons: T?, aospForced: T?): T? = native ?: lawnicons ?: aospForced
}
