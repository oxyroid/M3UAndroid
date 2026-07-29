package com.m3u.smartphone.ui.business.setting.fragments

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaylistManagementPresentationTest {
    @Test
    fun `playlist titles follow the current locale collation`() {
        val titles = listOf("Örebro", "Zebra", "Ängelholm", "Åland")

        assertEquals(
            listOf("Zebra", "Åland", "Ängelholm", "Örebro"),
            titles.sortedWith(
                playlistTitleComparator(Locale.forLanguageTag("sv-SE"))
            ),
        )
        assertEquals(
            listOf("Åland", "Ängelholm", "Örebro", "Zebra"),
            titles.sortedWith(
                playlistTitleComparator(Locale.forLanguageTag("en-US"))
            ),
        )
    }

    @Test
    fun `playlist title collation remains case insensitive`() {
        val comparator = playlistTitleComparator(Locale.ENGLISH)

        assertEquals(0, comparator.compare("News", "news"))
        assertEquals(0, comparator.compare("News\u202E", "News"))
    }
}
