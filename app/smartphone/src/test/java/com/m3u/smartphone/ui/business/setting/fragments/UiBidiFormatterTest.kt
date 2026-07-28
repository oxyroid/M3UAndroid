package com.m3u.smartphone.ui.business.setting.fragments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UiBidiFormatterTest {
    @Test
    fun `strip removes every bidi control accepted from dynamic metadata`() {
        val controls = "\u061C\u200E\u200F\u202A\u202B\u202C\u202D\u202E\u2066\u2067\u2068\u2069"

        assertEquals("Plugin name", "Plugin${controls} name".withoutBidiControls())
    }

    @Test
    fun `natural metadata is sanitized without changing its readable content`() {
        val formatted = "Safe\u202Eexe".withoutBidiControls()

        assertTrue(formatted.contains("Safeexe"))
        assertFalse(formatted.contains('\u202E'))
    }

    @Test
    fun `technical identifiers remain intact after sanitizing`() {
        val formatted = "com.example\u2066.plugin".withoutBidiControls()

        assertTrue(formatted.contains("com.example.plugin"))
        assertFalse(formatted.contains('\u2066'))
    }
}
