package com.m3u.data.database.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Refreshing a catalogue deletes every channel and imports it anew, which used
 * to reset what the viewer had built up: the Continue watching row emptied and
 * favourites disappeared on every update. These pin down the hand-over.
 */
class PreservedUserStatesTest {
    @Test
    fun `a watched channel keeps its history across an import`() {
        val states = PreservedUserStates(
            listOf(state(relationId = "12345", url = OLD_URL, seen = 1_700_000L)),
        )

        // The provider may hand back a different URL for the same title.
        val imported = channel(relationId = "12345", url = NEW_URL)
        val restored = imported.restoring(states, relationId = "12345")

        assertEquals(1_700_000L, restored.seen)
        assertEquals(NEW_URL, restored.url)
    }

    @Test
    fun `favourite and hidden flags come back too`() {
        val states = PreservedUserStates(
            listOf(state(relationId = "1", url = OLD_URL, favourite = true, hidden = true)),
        )

        val restored = channel(relationId = "1", url = NEW_URL).restoring(states, "1")

        assertTrue(restored.favourite)
        assertTrue(restored.hidden)
    }

    @Test
    fun `a playlist without relation ids falls back to the url`() {
        // An M3U without tvg-id has no stable id; the URL is all there is.
        val states = PreservedUserStates(
            listOf(state(relationId = null, url = OLD_URL, seen = 42L)),
        )

        val restored = channel(relationId = null, url = OLD_URL).restoring(states, null)

        assertEquals(42L, restored.seen)
    }

    @Test
    fun `a channel nobody touched is returned untouched`() {
        val states = PreservedUserStates(
            listOf(state(relationId = "known", url = OLD_URL, seen = 1L)),
        )

        val imported = channel(relationId = "other", url = NEW_URL)
        // Same instance, not a copy: nothing to hand over.
        assertSame(imported, imported.restoring(states, "other"))
        assertEquals(0L, imported.restoring(states, "other").seen)
    }

    @Test
    fun `a blank relation id does not match every blank one`() {
        val states = PreservedUserStates(
            listOf(state(relationId = "", url = OLD_URL, seen = 99L)),
        )

        // Falls through to the URL rather than matching anything blank.
        assertEquals(0L, channel(relationId = "", url = NEW_URL).restoring(states, "").seen)
        assertEquals(99L, channel(relationId = "", url = OLD_URL).restoring(states, "").seen)
    }

    @Test
    fun `nothing preserved leaves the import alone`() {
        val empty = PreservedUserStates(emptyList())
        assertTrue(empty.isEmpty)

        val imported = channel(relationId = "1", url = NEW_URL)
        assertSame(imported, imported.restoring(empty, "1"))
        assertSame(imported, imported.restoring(null, "1"))
    }

    @Test
    fun `the relation id wins over the url when both are known`() {
        val states = PreservedUserStates(
            listOf(
                state(relationId = "moved", url = OLD_URL, seen = 10L),
                state(relationId = "other", url = NEW_URL, seen = 20L),
            ),
        )

        // Same title, new URL: its own history follows it, not the one that
        // happens to sit at that URL now.
        val restored = channel(relationId = "moved", url = NEW_URL).restoring(states, "moved")
        assertEquals(10L, restored.seen)
        assertFalse(restored.favourite)
    }

    private fun state(
        relationId: String?,
        url: String,
        seen: Long = 0L,
        favourite: Boolean = false,
        hidden: Boolean = false,
    ) = ChannelUserState(
        relationId = relationId,
        url = url,
        seen = seen,
        favourite = favourite,
        hidden = hidden,
    )

    private fun channel(relationId: String?, url: String) = Channel(
        url = url,
        category = "Films",
        title = "Le Prénom",
        playlistUrl = PLAYLIST_URL,
        relationId = relationId,
    )

    private companion object {
        const val PLAYLIST_URL = "http://example.test/playlist"
        const val OLD_URL = "http://example.test/movie/1.mkv"
        const val NEW_URL = "http://example.test/movie/2.mkv"
    }
}
