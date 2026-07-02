package com.m3u.data.parser.epg

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class EpgParserImplTest {
    private val parser = EpgParserImpl()

    @Test
    fun readProgrammesParsesJsonEpgArray() = runBlocking {
        val input = """
            [
              {
                "channel_id": "76694",
                "channel_name": "NOW US",
                "start": "2026-07-01T19:15:00+02:00",
                "stop": "2026-07-01T20:00:00+02:00",
                "title": "Im Namen des Gesetzes",
                "description": "Episode description",
                "category": "Drama",
                "icon": "https://example.com/icon.png"
              }
            ]
        """.trimIndent()

        val programmes = parser
            .readProgrammes(ByteArrayInputStream(input.toByteArray()))
            .toList()

        assertEquals(1, programmes.size)
        assertEquals("76694", programmes.first().channel)
        assertEquals(listOf("NOW US"), programmes.first().channelAliases)
        assertEquals("2026-07-01T19:15:00+02:00", programmes.first().start)
        assertEquals("2026-07-01T20:00:00+02:00", programmes.first().stop)
        assertEquals("Im Namen des Gesetzes", programmes.first().title)
        assertEquals("Episode description", programmes.first().desc)
        assertEquals("https://example.com/icon.png", programmes.first().icon)
        assertEquals(listOf("Drama"), programmes.first().categories)
    }

    @Test
    fun readProgrammesParsesJsonObjectWithNestedProgrammes() = runBlocking {
        val input = """
            {
              "channel": {
                "id": "76694",
                "display_name": "NOW US"
              },
              "programmes": [
                {
                  "start_time": 1782926100,
                  "end_time": 1782928800,
                  "name": "Morning Show",
                  "summary": "Summary"
                }
              ]
            }
        """.trimIndent()

        val programmes = parser
            .readProgrammes(ByteArrayInputStream(input.toByteArray()))
            .toList()

        assertEquals(1, programmes.size)
        assertEquals("76694", programmes.first().channel)
        assertEquals(listOf("NOW US"), programmes.first().channelAliases)
        assertEquals("1782926100", programmes.first().start)
        assertEquals("1782928800", programmes.first().stop)
        assertEquals("Morning Show", programmes.first().title)
        assertEquals("Summary", programmes.first().desc)
    }

    @Test
    fun readProgrammesParsesJsonEpgWithUtf8Bom() = runBlocking {
        val input = """

            [
              {
                "channel_id": "bom-channel",
                "start": "2026-07-01T19:15:00+02:00",
                "stop": "2026-07-01T20:00:00+02:00",
                "title": "BOM Programme"
              }
            ]
        """.trimIndent()
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + input.toByteArray()

        val programmes = parser
            .readProgrammes(ByteArrayInputStream(bytes))
            .toList()

        assertEquals(1, programmes.size)
        assertEquals("bom-channel", programmes.first().channel)
        assertEquals("BOM Programme", programmes.first().title)
    }

    @Test
    fun readProgrammesParsesEpgPwJsonListAndInfersStopFromNextStart() = runBlocking {
        val input = """
            {
              "channel_id": "403793",
              "name": "EPG PW Channel",
              "epg_list": [
                {
                  "start_date": "2025-07-21T08:00:00+08:00",
                  "title": "Morning News",
                  "desc": "Headlines"
                },
                {
                  "start_date": "2025-07-21T09:00:00+08:00",
                  "title": "Market Update"
                }
              ]
            }
        """.trimIndent()

        val programmes = parser
            .readProgrammes(ByteArrayInputStream(input.toByteArray()))
            .toList()

        assertEquals(2, programmes.size)
        assertEquals("403793", programmes[0].channel)
        assertEquals(listOf("EPG PW Channel"), programmes[0].channelAliases)
        assertEquals("2025-07-21T08:00:00+08:00", programmes[0].start)
        assertEquals("2025-07-21T09:00:00+08:00", programmes[0].stop)
        assertEquals("Morning News", programmes[0].title)
        assertEquals("Headlines", programmes[0].desc)
        assertEquals("Market Update", programmes[1].title)
    }

    @Test
    fun readProgrammesParsesEpgPwJsonDataWithTimestampFields() = runBlocking {
        val input = """
            {
              "id": "403793",
              "name": "EPG PW Channel",
              "epg_data": [
                {
                  "start_timestamp": 1753056000,
                  "stop_timestamp": 1753059600,
                  "name": "Timestamp Programme",
                  "description": "Timestamp description",
                  "genre": "News"
                }
              ]
            }
        """.trimIndent()

        val programmes = parser
            .readProgrammes(ByteArrayInputStream(input.toByteArray()))
            .toList()

        assertEquals(1, programmes.size)
        assertEquals("403793", programmes.first().channel)
        assertEquals("1753056000", programmes.first().start)
        assertEquals("1753059600", programmes.first().stop)
        assertEquals("Timestamp Programme", programmes.first().title)
        assertEquals("Timestamp description", programmes.first().desc)
        assertEquals(listOf("News"), programmes.first().categories)
    }
}
