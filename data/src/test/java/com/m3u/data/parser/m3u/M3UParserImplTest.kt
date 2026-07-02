package com.m3u.data.parser.m3u

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import com.m3u.data.util.StreamUrlOptions
import org.junit.Assert.assertEquals
import org.junit.Test

class M3UParserImplTest {
    private val parser = M3UParserImpl()

    @Test
    fun parseTxtPlaylist() = runBlocking {
        val input = """
            News,#genre#
            CCTV+ Channel 1,http://example.com/channel1.m3u8#http://backup.example.com/channel1.m3u8
            Multicast,udp://239.49.0.1:1234
            Sports,#genre#
            CCTV5,https://example.com/cctv5.m3u8?fmt=ts2hls#http://backup.example.com/cctv5.m3u8
        """.trimIndent()

        val data = parser.parse(input.byteInputStream()).toList()

        assertEquals(3, data.size)
        assertEquals("News", data[0].group)
        assertEquals("CCTV+ Channel 1", data[0].title)
        assertEquals("http://example.com/channel1.m3u8", data[0].url)
        assertEquals("News", data[1].group)
        assertEquals("Multicast", data[1].title)
        assertEquals("udp://239.49.0.1:1234", data[1].url)
        assertEquals("Sports", data[2].group)
        assertEquals("CCTV5", data[2].title)
        assertEquals("https://example.com/cctv5.m3u8?fmt=ts2hls", data[2].url)
    }

    @Test
    fun parseM3UPlaylist() = runBlocking {
        val input = """
            #EXTM3U
            #EXTINF:-1 tvg-id="cctv1" tvg-name="CCTV 1" tvg-logo="https://example.com/logo.png" group-title="Live",CCTV1
            http://example.com/cctv1.m3u8
        """.trimIndent()

        val data = parser.parse(input.byteInputStream()).toList()

        assertEquals(1, data.size)
        assertEquals("cctv1", data.single().id)
        assertEquals("CCTV 1", data.single().name)
        assertEquals("https://example.com/logo.png", data.single().cover)
        assertEquals("Live", data.single().group)
        assertEquals("CCTV1", data.single().title)
        assertEquals("http://example.com/cctv1.m3u8", data.single().url)
    }

    @Test
    fun parseSeparateAudioAndVideoStreams() = runBlocking {
        val input = """
            #EXTM3U
            #EXTINF:-1 group-title="Radio",Weather Radio Cam
            https://example.com/radio.mp3;https://example.com/weather-cam.m3u8
        """.trimIndent()

        val data = parser.parse(input.byteInputStream()).toList()

        assertEquals(1, data.size)
        assertEquals("https://example.com/radio.mp3", data.single().url)
        assertEquals("https://example.com/weather-cam.m3u8", data.single().videoUrl)
        val channel = data.single().toChannel("https://playlist.example.com/list.m3u")
        assertEquals("https://example.com/radio.mp3", StreamUrlOptions.stripFromUrl(channel.url))
        assertEquals(
            "https://example.com/weather-cam.m3u8",
            StreamUrlOptions.readFromUrl(channel.url)[StreamUrlOptions.VIDEO_URL]
        )
    }
}
