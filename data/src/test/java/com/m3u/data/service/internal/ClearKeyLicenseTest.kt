package com.m3u.data.service.internal

import org.junit.Assert.assertEquals
import org.junit.Test

class ClearKeyLicenseTest {
    @Test
    fun normalizeSingleKodiClearKey() {
        val result = ClearKeyLicense.normalize(
            "a18b6aa739be4c0b114605fcfb5d6b68:b41c3a6f7511b2e3a828d9580124c89d"
        )

        assertEquals(
            """{"keys":[{"kty":"oct","kid":"oYtqpzm-TAsRRgX8-11raA","k":"tBw6b3URsuOoKNlYASTInQ"}],"type":"temporary"}""",
            result
        )
    }

    @Test
    fun normalizeMultipleKodiClearKeys() {
        val result = ClearKeyLicense.normalize(
            "{15965a6dbafd12c4af6aca127b271d5b:23dd40b93306de23ec667fb17a61f322," +
                "3decf356cc9351019fb1b627b089446d:4f7e516d3253d964e55b5c36f7f65d4a}"
        )

        assertEquals(
            """{"keys":[{"kty":"oct","kid":"FZZabbr9EsSvasoSeycdWw","k":"I91AuTMG3iPsZn-xemHzIg"},{"kty":"oct","kid":"PezzVsyTUQGfsbYnsIlEbQ","k":"T35RbTJT2WTlW1w29_ZdSg"}],"type":"temporary"}""",
            result
        )
    }

    @Test
    fun keepJsonClearKeyResponse() {
        val json = """{"keys":[],"type":"temporary"}"""

        assertEquals(json, ClearKeyLicense.normalize(json))
    }

    @Test
    fun keepRemoteLicenseUrl() {
        val url = "https://example.com/license"

        assertEquals(url, ClearKeyLicense.normalize(url))
    }
}
