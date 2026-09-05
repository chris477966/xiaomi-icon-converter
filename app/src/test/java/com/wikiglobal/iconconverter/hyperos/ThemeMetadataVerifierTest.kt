package com.wikiglobal.iconconverter.hyperos

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeMetadataVerifierTest {
    private val expected = ThemeFileMetadata("expected-sha", 100, 6101, 6102, "640", "u:object_r:theme_data_file:s0")
    private val actual = expected.copy(sha256 = "patched-sha")

    @Test fun `SHA_MISMATCH_REPORT`() = assertOnlyMismatch(actual.copy(sha256 = "wrong")) { !it.shaMatch }
    @Test fun `UID_MISMATCH_REPORT`() = assertOnlyMismatch(actual.copy(uid = 0)) { !it.uidMatch }
    @Test fun `GID_MISMATCH_REPORT`() = assertOnlyMismatch(actual.copy(gid = 0)) { !it.gidMatch }
    @Test fun `MODE_MISMATCH_REPORT`() = assertOnlyMismatch(actual.copy(mode = "600")) { !it.modeMatch }
    @Test fun `CONTEXT_MISMATCH_REPORT`() {
        val diff = ThemeMetadataVerifier.compare("patched-sha", expected, actual.copy(selinuxContext = "u:object_r:wrong:s0"))
        assertFalse(diff.contextMatch); assertFalse(diff.allMatch)
        assertTrue(ThemeMetadataVerifier.diagnostic(diff).contains("expectedContext=${expected.selinuxContext}"))
        assertTrue(ThemeMetadataVerifier.diagnostic(diff).contains("actualContext=u:object_r:wrong:s0"))
    }
    @Test fun `ALL_METADATA_MATCH_PASS`() {
        val diff = ThemeMetadataVerifier.compare("patched-sha", expected, actual)
        assertTrue(diff.allMatch)
    }

    private fun assertOnlyMismatch(value: ThemeFileMetadata, mismatch: (ThemeMetadataDiff) -> Boolean) {
        val diff = ThemeMetadataVerifier.compare("patched-sha", expected, value)
        assertTrue(mismatch(diff)); assertFalse(diff.allMatch)
    }
}
