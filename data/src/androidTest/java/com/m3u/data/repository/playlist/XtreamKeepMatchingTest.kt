package com.m3u.data.repository.playlist

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamKeepMatchingTest {
    @Test
    fun stableIdIsScopedToItsPlaylist() {
        val liveUrl = "https://provider.example/player_api.php?type=live"
        val vodUrl = "https://provider.example/player_api.php?type=vod"
        val preserved = mapOf(liveUrl to setOf("42"))

        assertTrue(
            isXtreamRelationPreserved(
                playlistUrl = liveUrl,
                relationId = "42",
                preservedRelationIdsByPlaylistUrl = preserved,
            )
        )
        assertFalse(
            isXtreamRelationPreserved(
                playlistUrl = vodUrl,
                relationId = "42",
                preservedRelationIdsByPlaylistUrl = preserved,
            )
        )
    }

    @Test
    fun missingStableIdIsNeverMatched() {
        assertFalse(
            isXtreamRelationPreserved(
                playlistUrl = "https://provider.example/player_api.php?type=series",
                relationId = null,
                preservedRelationIdsByPlaylistUrl = mapOf(
                    "https://provider.example/player_api.php?type=series" to setOf("null")
                ),
            )
        )
    }
}
