package com.m3u.smartphone.startup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DebugDefaultLibraryStateTest {
    @Test
    fun `imported state round trips its revision and playlist identity`() {
        val state = DebugDefaultLibraryBootstrapState(
            status = DebugDefaultLibraryBootstrapStatus.IMPORTED,
            revision = "2026-07-29.2",
            playlistUrl = "file:///data/user/0/com.m3u.smartphone/files/playlists/sample.m3u",
        )

        assertEquals(
            state,
            DebugDefaultLibraryBootstrapStateCodec.decodeOrNull(
                DebugDefaultLibraryBootstrapStateCodec.encode(state)
            ),
        )
    }

    @Test
    fun `legacy imported state requests one canonical update`() {
        val legacy = checkNotNull(
            DebugDefaultLibraryBootstrapStateCodec.decodeOrNull("imported")
        )

        assertTrue(legacy.needsAssetUpdate("2026-07-29.2"))
    }

    @Test
    fun `matching revision is already current`() {
        val state = DebugDefaultLibraryBootstrapState(
            status = DebugDefaultLibraryBootstrapStatus.IMPORTED,
            revision = "2026-07-29.2",
            playlistUrl = "file:///sample.m3u",
        )

        assertFalse(state.needsAssetUpdate("2026-07-29.2"))
    }

    @Test
    fun `opted out state remains independent of asset revisions`() {
        val state = DebugDefaultLibraryBootstrapState(
            status = DebugDefaultLibraryBootstrapStatus.OPTED_OUT,
        )

        assertFalse(state.needsAssetUpdate("2026-07-29.2"))
        assertEquals(
            state,
            DebugDefaultLibraryBootstrapStateCodec.decodeOrNull(
                DebugDefaultLibraryBootstrapStateCodec.encode(state)
            ),
        )
    }

    @Test
    fun `invalid or partial canonical state is rejected`() {
        assertNull(
            DebugDefaultLibraryBootstrapStateCodec.decodeOrNull(
                """{"schemaVersion":1,"status":"imported","revision":"test.2"}"""
            )
        )
        assertNull(
            DebugDefaultLibraryBootstrapStateCodec.decodeOrNull(
                """{"schemaVersion":2,"status":"opted-out"}"""
            )
        )
    }
}
