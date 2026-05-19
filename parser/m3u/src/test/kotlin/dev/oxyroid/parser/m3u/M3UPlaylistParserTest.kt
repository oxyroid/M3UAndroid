package dev.oxyroid.parser.m3u

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class M3UPlaylistParserTest {
    @Test
    fun parsesExtendedM3UWithIptvMetadataAndKodiProperties() {
        val channels = playlist(
            """
            #EXTM3U
            #KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha
            #KODIPROP:inputstream.adaptive.license_key=https://license.example.test
            #EXTINF:-1 tvg-id="news.id" tvg-name="News Name" tvg-logo="https://logo.example.test/news.png" group-title="News",News Title
            https://media.example.test/news.m3u8
            """.trimIndent()
        )

        assertEquals(1, channels.size)
        channels.single().also { channel ->
            assertEquals("news.id", channel.id)
            assertEquals("News Name", channel.name)
            assertEquals("https://logo.example.test/news.png", channel.cover)
            assertEquals("News", channel.group)
            assertEquals("News Title", channel.title)
            assertEquals("https://media.example.test/news.m3u8", channel.url)
            assertEquals(-1.0, channel.duration)
            assertEquals("com.widevine.alpha", channel.licenseType)
            assertEquals("https://license.example.test", channel.licenseKey)
        }
    }

    @Test
    fun parsesHlsMediaPlaylistSegmentsFromRfcStyleExtinf() {
        val channels = playlist(
            """
            #EXTM3U
            #EXT-X-TARGETDURATION:10
            #EXTINF:9.009,
            first.ts
            #EXTINF:3.003,Short segment
            http://media.example.com/third.ts
            """.trimIndent()
        )

        assertEquals(2, channels.size)
        assertEquals(9.009, channels[0].duration)
        assertEquals("first.ts", channels[0].url)
        assertEquals("first.ts", channels[0].title)
        assertEquals(3.003, channels[1].duration)
        assertEquals("Short segment", channels[1].title)
    }

    @Test
    fun parsesHlsMasterPlaylistVariantUris() {
        val channels = playlist(
            """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1280000,RESOLUTION=640x360,NAME="Low"
            low/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2560000,RESOLUTION=1280x720
            high/index.m3u8
            """.trimIndent()
        )

        assertEquals(2, channels.size)
        assertEquals("Low", channels[0].title)
        assertEquals("low/index.m3u8", channels[0].url)
        assertEquals("1280x720", channels[1].title)
        assertEquals("high/index.m3u8", channels[1].url)
    }

    @Test
    fun parsesPlainM3UAndVlcOptions() {
        val channels = playlist(
            """
            #EXTM3U
            #EXTGRP:Radio
            #EXTVLCOPT:http-user-agent=VLC/3.0
            #EXTVLCOPT:http-referrer=https://example.test
            udp://@239.0.0.1:1234
            vlc://pause:5
            """.trimIndent()
        )

        assertEquals(2, channels.size)
        channels[0].also { channel ->
            assertEquals("Radio", channel.group)
            assertEquals("@239.0.0.1:1234", channel.title)
            assertEquals("VLC/3.0", channel.userAgent)
            assertEquals("https://example.test", channel.referrer)
        }
        channels[1].also { channel ->
            assertEquals("vlc://pause:5", channel.url)
            assertEquals("pause:5", channel.title)
            assertNull(channel.userAgent)
        }
    }

    private fun playlist(value: String) = M3UPlaylistParser
        .parse(value.byteInputStream())
        .toList()
}
