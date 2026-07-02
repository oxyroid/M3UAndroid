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

    @Test
    fun toProgrammesTrimsChannelAliases() {
        val programme = EpgProgramme(
            channel = " cctv1 ",
            channelAliases = listOf(" CCTV 1 ", "CCTV 1"),
            start = "20260703080000 +0800",
            stop = "20260703090000 +0800",
            title = "Morning News",
            desc = "Headlines",
            categories = emptyList()
        )

        val programmes = programme.toProgrammes("https://example.com/epg.xml")

        assertEquals(
            listOf("cctv1", "CCTV 1"),
            programmes.map { it.channelId }
        )
    }

    @Test
    fun readEpochMillisecondsSupportsIsoOffsetTime() {
        val timestamp = EpgProgramme.readEpochMilliseconds("2026-07-01T19:15:00+02:00")

        assertEquals(1782926100000L, timestamp)
    }

    @Test
    fun readEpochMillisecondsSupportsCompactOffsetWithoutSpace() {
        val timestamp = EpgProgramme.readEpochMilliseconds("20260702011500+0800")

        assertEquals(1782926100000L, timestamp)
    }

    @Test
    fun readEpochMillisecondsSupportsSpaceSeparatedOffsetTime() {
        val timestamp = EpgProgramme.readEpochMilliseconds("2026-07-02 01:15:00 +0800")

        assertEquals(1782926100000L, timestamp)
    }

    @Test
    fun readEpochMillisecondsSupportsEpochSeconds() {
        val timestamp = EpgProgramme.readEpochMilliseconds("1782926100")

        assertEquals(1782926100000L, timestamp)
    }
}
