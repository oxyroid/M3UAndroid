package com.m3u.business.foryou

import com.m3u.data.database.model.Playlist
import org.junit.Assert.assertEquals
import org.junit.Test

class RecommendTest {
    @Test
    fun discoverSpecsUsePinnedCategories() {
        val playlist = playlist(
            title = "News",
            url = "https://example.com/news.m3u",
            pinnedCategories = listOf("World", "Local")
        )

        val specs = Recommend.discoverSpecs(mapOf(playlist to 42), limit = 8)

        assertEquals(
            listOf(
                Recommend.DiscoverSpec(playlist, "World"),
                Recommend.DiscoverSpec(playlist, "Local")
            ),
            specs
        )
    }

    @Test
    fun discoverSpecsIgnoreHiddenBlankAndEmptyPlaylists() {
        val visible = playlist(
            title = "Visible",
            url = "https://example.com/visible.m3u",
            pinnedCategories = listOf("News", "", "Sports"),
            hiddenCategories = listOf("Sports")
        )
        val empty = playlist(
            title = "Empty",
            url = "https://example.com/empty.m3u",
            pinnedCategories = listOf("Music")
        )

        val specs = Recommend.discoverSpecs(
            playlists = mapOf(visible to 10, empty to 0),
            limit = 8
        )

        assertEquals(listOf(Recommend.DiscoverSpec(visible, "News")), specs)
    }

    @Test
    fun discoverSpecsPreferLargerPlaylists() {
        val smaller = playlist(
            title = "Smaller",
            url = "https://example.com/smaller.m3u",
            pinnedCategories = listOf("Drama")
        )
        val larger = playlist(
            title = "Larger",
            url = "https://example.com/larger.m3u",
            pinnedCategories = listOf("News")
        )

        val specs = Recommend.discoverSpecs(
            playlists = linkedMapOf(smaller to 20, larger to 100),
            limit = 8
        )

        assertEquals(
            listOf(
                Recommend.DiscoverSpec(larger, "News"),
                Recommend.DiscoverSpec(smaller, "Drama")
            ),
            specs
        )
    }

    @Test
    fun discoverSpecsRespectLimit() {
        val playlist = playlist(
            title = "Many",
            url = "https://example.com/many.m3u",
            pinnedCategories = listOf("One", "Two", "Three")
        )

        val specs = Recommend.discoverSpecs(mapOf(playlist to 10), limit = 2)

        assertEquals(
            listOf(
                Recommend.DiscoverSpec(playlist, "One"),
                Recommend.DiscoverSpec(playlist, "Two")
            ),
            specs
        )
    }

    private fun playlist(
        title: String,
        url: String,
        pinnedCategories: List<String>,
        hiddenCategories: List<String> = emptyList()
    ): Playlist {
        return Playlist(
            title = title,
            url = url,
            pinnedCategories = pinnedCategories,
            hiddenCategories = hiddenCategories
        )
    }
}
