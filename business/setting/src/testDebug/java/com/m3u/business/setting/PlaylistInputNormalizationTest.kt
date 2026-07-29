package com.m3u.business.setting

import com.m3u.core.foundation.util.basic.PlaylistInputKind
import com.m3u.core.foundation.util.basic.normalizePlaylistInputForSubmission
import com.m3u.core.foundation.util.basic.sanitizePlaylistInput
import com.m3u.data.database.model.DataSource
import com.m3u.data.parser.xtream.XtreamInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlaylistInputNormalizationTest {
    @Test
    fun `bidi and control characters are removed without reversing rtl text`() {
        val bidiControls = buildString {
            append('\u061C')
            append('\u200E')
            append('\u200F')
            (0x202A..0x202E).forEach { append(it.toChar()) }
            (0x2066..0x2069).forEach { append(it.toChar()) }
        }
        val value = "\u0000قناة$bidiControls عربية\t\n\u0085\u2028\u2029"

        assertEquals(
            "قناة عربية",
            value.sanitizePlaylistInput(PlaylistInputKind.TITLE),
        )
    }

    @Test
    fun `submission trims semantic text fields but preserves password spaces`() {
        assertEquals(
            "Living room",
            "  Living room  ".normalizePlaylistInputForSubmission(
                PlaylistInputKind.TITLE
            ),
        )
        assertEquals(
            "https://example.test/list.m3u",
            " \nhttps://example.test/list.m3u\r ".normalizePlaylistInputForSubmission(
                PlaylistInputKind.URL
            ),
        )
        assertEquals(
            " secret phrase ",
            "\n secret phrase \r".normalizePlaylistInputForSubmission(
                PlaylistInputKind.PASSWORD
            ),
        )
    }

    @Test
    fun `limits are enforced by characters and utf8 bytes without splitting emoji`() {
        val asciiTitle = "a".repeat(PlaylistInputKind.TITLE.maximumCharacters + 20)
        assertEquals(
            PlaylistInputKind.TITLE.maximumCharacters,
            asciiTitle.sanitizePlaylistInput(PlaylistInputKind.TITLE).length,
        )

        val emojiTitle = "🙂".repeat(PlaylistInputKind.TITLE.maximumCharacters + 20)
            .sanitizePlaylistInput(PlaylistInputKind.TITLE)
        assertEquals(
            PlaylistInputKind.TITLE.maximumUtf8Bytes,
            emojiTitle.encodeToByteArray().size,
        )
        assertFalse(emojiTitle.last().isHighSurrogate())

        val multibyteUrl = "é".repeat(PlaylistInputKind.URL.maximumCharacters)
            .sanitizePlaylistInput(PlaylistInputKind.URL)
        assertTrue(
            multibyteUrl.encodeToByteArray().size <=
                PlaylistInputKind.URL.maximumUtf8Bytes
        )
    }

    @Test
    fun `invalid standalone surrogates never enter persisted input`() {
        assertEquals(
            "safe",
            "\uD800safe\uDC00".sanitizePlaylistInput(PlaylistInputKind.TITLE),
        )
    }

    @Test
    fun `clipboard title only uses safe path filename`() {
        val input = ClipboardPlaylistInput.parse(
            rawUrl =
                " https://viewer:private@example.test/live/list.m3u" +
                    "?token=top-secret#password=hidden ",
            source = DataSource.M3U,
        )

        assertEquals("list", input.title)
        assertTrue("top-secret" in assertNotNull(input.m3uUrl))
        assertEquals(null, input.xtreamInput)
        assertFalse("private" in input.title)
        assertFalse("top-secret" in input.title)
        assertFalse("hidden" in input.title)
    }

    @Test
    fun `clipboard without safe path leaves title for the localized form`() {
        val input = ClipboardPlaylistInput.parse(
            rawUrl = "https://viewer:private@example.test?token=top-secret",
            source = DataSource.M3U,
        )

        assertEquals("", input.title)
    }

    @Test
    fun `clipboard filename is decoded then sanitized`() {
        val input = ClipboardPlaylistInput.parse(
            rawUrl = "https://example.test/%E2%80%AEchannels.m3u?token=secret",
            source = DataSource.M3U,
        )

        assertEquals("channels", input.title)
    }

    @Test
    fun `xtream clipboard uses normalized parsed fields and preserves password spaces`() {
        val input = ClipboardPlaylistInput.parse(
            rawUrl =
                " https://example.test/player_api.php" +
                    "?username=viewer&password=%20secret%20&xtream_type=live\n",
            source = DataSource.Xtream,
        )
        val xtream = assertNotNull(input.xtreamInput)

        assertEquals("", input.title)
        assertEquals(null, input.m3uUrl)
        assertEquals("viewer", xtream.username)
        assertEquals(" secret ", xtream.password)
        assertEquals(DataSource.Xtream.TYPE_LIVE, xtream.type)
    }

    @Test
    fun `invalid xtream clipboard has no m3u reference or parsed credentials`() {
        val input = ClipboardPlaylistInput.parse(
            rawUrl = "not a valid account?token=secret",
            source = DataSource.Xtream,
        )

        assertEquals(null, input.m3uUrl)
        assertEquals(null, input.xtreamInput)
        assertFalse("secret" in input.title)
    }

    @Test
    fun `xtream reference is rebuilt from edited account instead of clipboard credentials`() {
        val rebuilt = buildXtreamPlaylistUrlOrEmpty(
            basicUrl = "https://new.example.test",
            username = "new-user",
            password = " new-secret ",
            type = DataSource.Xtream.TYPE_SERIES,
        )
        val decoded = assertNotNull(XtreamInput.decodeFromPlaylistUrlOrNull(rebuilt))

        assertEquals("new-user", decoded.username)
        assertEquals(" new-secret ", decoded.password)
        assertEquals(DataSource.Xtream.TYPE_SERIES, decoded.type)
        assertTrue(rebuilt.startsWith("https://"))
    }

    @Test
    fun `draft session resets for cross-source and reopened editor keys`() {
        val session = SubscriptionDraftSession()

        assertTrue(session.begin("m3u:first"))
        assertFalse(session.begin("m3u:first"))
        assertTrue(session.begin("xtream:first"))
        assertTrue(session.begin("m3u:reopened"))
    }
}
