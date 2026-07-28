package com.m3u.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvBidiFormatterTest {
    @Test
    fun `strip removes every bidi control accepted from extension metadata`() {
        val controls = "\u061C\u200E\u200F\u202A\u202B\u202C\u202D\u202E\u2066\u2067\u2068\u2069"

        assertEquals("Section title", "Section${controls} title".withoutBidiControls())
    }

    @Test
    fun `display text keeps readable content after removing spoofing controls`() {
        val sanitized = "Provider\u202Eexe".withoutBidiControls()

        assertTrue(sanitized.contains("Providerexe"))
        assertFalse(sanitized.contains('\u202E'))
    }

    @Test
    fun `technical identifier keeps its exact character order`() {
        val sanitized = "provider.example\u2066.kind".withoutBidiControls()

        assertEquals("provider.example.kind", sanitized)
    }
}
