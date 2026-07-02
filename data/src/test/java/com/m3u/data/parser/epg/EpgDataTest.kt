package com.m3u.data.parser.epg

import org.junit.Assert.assertEquals
import org.junit.Test

class EpgDataTest {
    @Test
    fun toProgrammesIncludesDisplayNameAliases() {
        val programme = EpgProgramme(
            channel = "bbc.one.uk",
            channelAliases = listOf("BBC One", "BBC 1", "BBC One"),
            start = "20260703080000 +0800",
            stop = "20260703090000 +0800",
            title = "Morning News",
            desc = "Headlines",
            icon = "https://example.com/icon.png",
            categories = listOf("News")
        )

        val programmes = programme.toProgrammes("https://example.com/epg.xml")

        assertEquals(
            listOf("bbc.one.uk", "BBC One", "BBC 1"),
            programmes.map { it.channelId }
        )
        assertEquals("Morning News", programmes.first().title)
        assertEquals("Headlines", programmes.first().description)
        assertEquals("https://example.com/icon.png", programmes.first().icon)
        assertEquals(listOf("News"), programmes.first().categories)
    }
}
