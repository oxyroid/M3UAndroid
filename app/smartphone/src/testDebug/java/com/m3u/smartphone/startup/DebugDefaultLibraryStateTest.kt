package com.m3u.smartphone.startup

import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
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
            playlistSha256 = TEST_PLAYLIST_SHA256,
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
    fun `canonical state selects only its recorded playlist identity`() {
        val ownedPlaylist = Playlist(
            title = "Renamed samples",
            url = "file:///owned/default-library.m3u",
            source = DataSource.M3U,
        )
        val sameTitledUserPlaylist = Playlist(
            title = ownedPlaylist.title,
            url = "https://user.example/playlist.m3u",
            source = DataSource.M3U,
        )
        val state = DebugDefaultLibraryBootstrapState(
            status = DebugDefaultLibraryBootstrapStatus.IMPORTED,
            revision = "2026-07-29.2",
            playlistSha256 = TEST_PLAYLIST_SHA256,
            playlistUrl = ownedPlaylist.url,
        )

        assertEquals(
            ownedPlaylist,
            selectTrackedDefaultLibraryPlaylist(
                state = state,
                currentPlaylists = listOf(
                    sameTitledUserPlaylist,
                    ownedPlaylist,
                ),
            ),
        )
    }

    @Test
    fun `matching revision is already current`() {
        val state = DebugDefaultLibraryBootstrapState(
            status = DebugDefaultLibraryBootstrapStatus.IMPORTED,
            revision = "2026-07-29.2",
            playlistSha256 = TEST_PLAYLIST_SHA256,
            playlistUrl = "file:///sample.m3u",
        )

        assertFalse(
            state.needsAssetUpdate(
                targetRevision = "2026-07-29.2",
                targetPlaylistSha256 = TEST_PLAYLIST_SHA256,
            )
        )
    }

    @Test
    fun `changed playlist content requests update even when revision is unchanged`() {
        val state = DebugDefaultLibraryBootstrapState(
            status = DebugDefaultLibraryBootstrapStatus.IMPORTED,
            revision = "2026-07-29.2",
            playlistSha256 = TEST_PLAYLIST_SHA256,
            playlistUrl = "file:///sample.m3u",
        )

        assertTrue(
            state.needsAssetUpdate(
                targetRevision = "2026-07-29.2",
                targetPlaylistSha256 = "b".repeat(64),
            )
        )
    }

    @Test
    fun `opted out state remains independent of asset revisions`() {
        val state = DebugDefaultLibraryBootstrapState(
            status = DebugDefaultLibraryBootstrapStatus.OPTED_OUT,
        )

        assertFalse(
            state.needsAssetUpdate(
                targetRevision = "2026-07-29.2",
                targetPlaylistSha256 = TEST_PLAYLIST_SHA256,
            )
        )
        assertEquals(
            state,
            DebugDefaultLibraryBootstrapStateCodec.decodeOrNull(
                DebugDefaultLibraryBootstrapStateCodec.encode(state)
            ),
        )
    }

    @Test
    fun `draft and partial state formats are rejected`() {
        assertNull(DebugDefaultLibraryBootstrapStateCodec.decodeOrNull("imported"))
        assertNull(
            DebugDefaultLibraryBootstrapStateCodec.decodeOrNull(
                """{"schemaVersion":1,"status":"imported","revision":"test.2"}"""
            )
        )
        assertNull(
            DebugDefaultLibraryBootstrapStateCodec.decodeOrNull(
                """{"schemaVersion":3,"status":"opted-out"}"""
            )
        )
        assertNull(
            DebugDefaultLibraryBootstrapStateCodec.decodeOrNull(
                """
                    {
                      "schemaVersion": 2,
                      "status": "imported",
                      "revision": "test.2",
                      "playlistSha256": "$TEST_PLAYLIST_SHA256",
                      "playlistUrl": "file:///sample.m3u"
                    }
                """.trimIndent()
            )
        )
    }

    private companion object {
        val TEST_PLAYLIST_SHA256 = "a".repeat(64)
    }
}
