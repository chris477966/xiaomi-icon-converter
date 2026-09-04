package com.wikiglobal.iconconverter.hyperos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class LawniconsThemedProviderTest {
    private val entries = listOf(
        LawniconsThemeEntry("com.android.settings", 0x7f041876, "settings"),
        LawniconsThemeEntry("com.miui.gallery", 0x7f04183e, "gallery"),
        LawniconsThemeEntry("com.miui.calculator", 0x7f041100, "calculator")
    )

    @Test fun `Lawnchair style grayscale map uses package only lookup`() {
        val index = LawniconsThemedIndex(entries)
        assertEquals(0x7f041876, index.lookup("com.android.settings")!!.drawableResourceId)
        assertEquals("settings", index.lookup("com.android.settings")!!.drawableName)
        assertNull(index.lookup("com.android.settings#com.android.settings.Settings"))
    }

    @Test fun `settings is available even without an appfilter activity entry`() {
        assertTrue(LawniconsThemedIndex(entries).lookup("com.android.settings") != null)
    }

    @Test fun `bundled Lawnicons apk has the published SHA256`() {
        val file = File("src/main/assets/providers/Lawnicons.2.18.0.apk")
        assertTrue(file.isFile)
        val sha = file.inputStream().use { input -> MessageDigest.getInstance("SHA-256").digest(input.readBytes()).joinToString("") { "%02x".format(it) } }
        assertEquals(LawniconsProvider.SHA256, sha)
    }
}
