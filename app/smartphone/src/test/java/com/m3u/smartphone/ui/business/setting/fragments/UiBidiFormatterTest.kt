package com.m3u.smartphone.ui.business.setting.fragments

import com.m3u.smartphone.ui.material.ktx.UiBidiFormatter
import com.m3u.smartphone.ui.material.ktx.safeDisplayText
import com.m3u.smartphone.ui.material.ktx.withoutBidiControls
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

    @Test
    fun `display sanitizing removes controls without reversing rtl words`() {
        val formatted = "العربية\u202E\nقناة\u2029".safeDisplayText()

        assertEquals("العربيةقناة", formatted)
        assertFalse(formatted.any(Char::isISOControl))
    }

    @Test
    fun `standalone technical values are not truncated at the display metadata limit`() {
        val host = List(4) { index ->
            ('a' + index).toString().repeat(61)
        }.joinToString(".")
        val url = "https://$host:443"

        assertTrue(url.length > 256)
        assertEquals(
            url,
            UiBidiFormatter(isRtlContext = false).standaloneTechnical(url),
        )
    }

    @Test
    fun `rtl context does not wrap or reverse standalone technical values`() {
        val value = "com.example.extension.ReferenceService"
        val formatted = UiBidiFormatter(isRtlContext = true)
            .standaloneTechnical("com.example\u202E.extension\u2066.ReferenceService\u2069")

        assertEquals(value, formatted)
        assertFalse(formatted.any(::isBidiControl))
    }

    @Test
    fun `standalone technical values remove non bidi dangerous characters`() {
        val formatted = UiBidiFormatter(isRtlContext = true)
            .standaloneTechnical("https://api\u0000\t\n\u2028\u2029.example.test:443")

        assertEquals("https://api.example.test:443", formatted)
        assertFalse(formatted.any(Char::isISOControl))
    }

    private fun isBidiControl(character: Char): Boolean =
        character.code in 0x202A..0x202E ||
            character.code in 0x2066..0x2069 ||
            character.code == 0x061C ||
            character.code == 0x200E ||
            character.code == 0x200F
}
