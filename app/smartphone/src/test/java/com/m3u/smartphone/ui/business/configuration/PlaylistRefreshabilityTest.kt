package com.m3u.smartphone.ui.business.configuration

import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.refreshable
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaylistRefreshabilityTest {
    @Test
    fun `remote subscriptions expose refresh while local snapshots do not`() {
        assertTrue(playlist(DataSource.M3U, "https://example.com/list.m3u").refreshable)
        assertTrue(playlist(DataSource.Xtream, "xtream://account").refreshable)
        assertTrue(playlist(DataSource.Provider, "provider://account").refreshable)

        assertFalse(playlist(DataSource.M3U, "content://media/list.m3u").refreshable)
        assertFalse(playlist(DataSource.M3U, "file:///tmp/list.m3u").refreshable)
        assertFalse(playlist(DataSource.M3U, Playlist.URL_IMPORTED).refreshable)
        assertFalse(playlist(DataSource.EPG, "https://example.com/guide.xml").refreshable)
    }

    private fun playlist(source: DataSource, url: String): Playlist = Playlist(
        title = "Test",
        url = url,
        source = source,
    )
}
