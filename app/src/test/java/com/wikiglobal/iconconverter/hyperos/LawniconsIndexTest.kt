package com.wikiglobal.iconconverter.hyperos

import android.graphics.drawable.ColorDrawable
import com.wikiglobal.iconconverter.model.*
import org.junit.Assert.*
import org.junit.Test

class LawniconsIndexTest {
    private fun app(activity:String="A")=InstalledApp("p",activity,"p",ColorDrawable(),activityAliases=setOf(activity))
    @Test fun `exact wins`() { val i=LawniconsIndex(listOf(IconMapping(ComponentKey("p","A"),"one"),IconMapping(ComponentKey("p","B"),"two")));assertEquals(LawniconsMatchType.LAWNICONS_EXACT,i.match(app())!!.second) }
    @Test fun `unique package fallback works`() { val i=LawniconsIndex(listOf(IconMapping(ComponentKey("p","A"),"one"),IconMapping(ComponentKey("p","B"),"one")));assertEquals(LawniconsMatchType.LAWNICONS_PACKAGE_FALLBACK,i.match(app("C"))!!.second) }
    @Test fun `ambiguous package is refused`() { val i=LawniconsIndex(listOf(IconMapping(ComponentKey("p","A"),"one"),IconMapping(ComponentKey("p","B"),"two")));assertNull(i.match(app("C"))) }
}
