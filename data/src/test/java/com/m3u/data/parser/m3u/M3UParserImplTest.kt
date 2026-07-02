package com.m3u.data.parser.m3u

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import com.m3u.data.database.model.Channel
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
    fun parseTxtPlaylistWithContentUris() = runBlocking {
        val input = """
            Local,#genre#
            Local audio,content://com.example.provider/audio.m3u8
            Separate local video,http://example.com/audio.mp3;content://com.example.provider/video.m3u8
        """.trimIndent()

        val data = parser.parse(input.byteInputStream()).toList()

        assertEquals(2, data.size)
        assertEquals("Local audio", data[0].title)
        assertEquals("content://com.example.provider/audio.m3u8", data[0].url)
        assertEquals("Separate local video", data[1].title)
        assertEquals("http://example.com/audio.mp3", data[1].url)
        assertEquals("content://com.example.provider/video.m3u8", data[1].videoUrl)
    }

    @Test
    fun parseIssue326TxtPlaylistExample() = runBlocking {
        val input = """
            中国央视,#genre#
            CCTV+ Channel 1,http://cd-live-stream.news.cctvplus.com/live/smil:CHANNEL1.smil/chunklist_w744036192_b1000000.m3u8
            央视新闻·正直播,https://newsalic.v.myalicdn.com:443/news/news10_1/index.m3u8
            河南卫视 高清,http://39.135.138.8:6610/PLTV/88888888/224/3221225611/2/index.m3u8?fmt=ts2hls#https://www.navchina.cf/IPTV/hn.php?id=hnws#https://www.navchina.cf/IPTV/zhengzhou.php?id=hnws
            中国卫视,#genre#
            湖南卫视,http://39.135.138.8:6610/PLTV/88888888/224/3221225704/2/index.m3u8?fmt=ts2hls#http://111.20.105.60:6060/yinhe/2/ch00000090990000001339/index.m3u8?virtualDomain=yinhe.live_hls.zte.com
        """.trimIndent()

        val data = parser.parse(input.byteInputStream()).toList()

        assertEquals(4, data.size)
        assertEquals("中国央视", data[0].group)
        assertEquals("CCTV+ Channel 1", data[0].title)
        assertEquals(
            "http://cd-live-stream.news.cctvplus.com/live/smil:CHANNEL1.smil/chunklist_w744036192_b1000000.m3u8",
            data[0].url
        )
        assertEquals("央视新闻·正直播", data[1].title)
        assertEquals("https://newsalic.v.myalicdn.com:443/news/news10_1/index.m3u8", data[1].url)
        assertEquals("河南卫视 高清", data[2].title)
        assertEquals(
            "http://39.135.138.8:6610/PLTV/88888888/224/3221225611/2/index.m3u8?fmt=ts2hls",
            data[2].url
        )
        assertEquals("中国卫视", data[3].group)
        assertEquals("湖南卫视", data[3].title)
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
    fun parseM3UPlaylistWithDecimalDuration() = runBlocking {
        val input = """
            #EXTM3U
            #EXTINF:-1.0 tvg-id="decimal" group-title="Live",Decimal Duration
            https://example.com/decimal.m3u8
        """.trimIndent()

        val data = parser.parse(input.byteInputStream()).toList()

        assertEquals(1, data.size)
        assertEquals("decimal", data.single().id)
        assertEquals("Decimal Duration", data.single().title)
        assertEquals(-1.0, data.single().duration, 0.0)
        assertEquals("https://example.com/decimal.m3u8", data.single().url)
    }

    @Test
    fun parseM3UPlaylistWithEmptyTitleFallsBackToTvgName() = runBlocking {
        val input = """
            #EXTM3U
            #EXTINF:-1 tvg-id="fallback" tvg-name="Fallback Channel" group-title="Live",
            https://example.com/fallback.m3u8
        """.trimIndent()

        val data = parser.parse(input.byteInputStream()).toList()

        assertEquals(1, data.size)
        assertEquals("fallback", data.single().id)
        assertEquals("Fallback Channel", data.single().name)
        assertEquals("Fallback Channel", data.single().title)
        assertEquals("Live", data.single().group)
        assertEquals("https://example.com/fallback.m3u8", data.single().url)
    }

    @Test
    fun parseM3UPlaylistWithBomAndIndentedProtocolLines() = runBlocking {
        val input = "\uFEFF  #EXTM3U x-tvg-url=\"https://example.com/epg.xml\"\n" +
            "  #EXTINF:-1 tvg-id=\"radio\" group-title=\"Radio\",Radio One\n" +
            "  #EXTVLCOPT:http-user-agent=MockAgent/7.0\n" +
            "  https://example.com/radio.m3u8\n"

        val data = parser.parse(input.byteInputStream()).toList()
        val channel = data.single().toChannel("https://playlist.example.com/list.m3u")
        val options = StreamUrlOptions.readFromUrl(channel.url)

        assertEquals("radio", data.single().id)
        assertEquals("Radio", data.single().group)
        assertEquals("Radio One", data.single().title)
        assertEquals(listOf("https://example.com/epg.xml"), data.single().playlistEpgUrls)
        assertEquals("https://example.com/radio.m3u8", StreamUrlOptions.stripFromUrl(channel.url))
        assertEquals("MockAgent/7.0", options[StreamUrlOptions.USER_AGENT])
    }

    @Test
    fun parseM3UPlaylistWithCaseInsensitiveProtocolAndMetadataKeys() = runBlocking {
        val input = """
            #extm3u TVG-URL="https://example.com/epg.xml"
            #extinf:-1 TVG-ID="case-id" TVG-NAME="Case Name" GROUP-TITLE="Live",Case Channel
            #extvlcopt:HTTP-USER-AGENT=CaseAgent/1.0
            #kodiprop:INPUTSTREAM.ADAPTIVE.LICENSE_TYPE=ClearKey
            #kodiprop:INPUTSTREAM.ADAPTIVE.LICENSE_KEY=abc:def
            #exthttp:{"Cookie":"session=case"}
            https://example.com/case.mpd
        """.trimIndent()

        val data = parser.parse(input.byteInputStream()).toList()
        val channel = data.single().toChannel("https://playlist.example.com/list.m3u")
        val options = StreamUrlOptions.readFromUrl(channel.url)

        assertEquals("case-id", data.single().id)
        assertEquals("Case Name", data.single().name)
        assertEquals("Live", data.single().group)
        assertEquals("Case Channel", data.single().title)
        assertEquals(listOf("https://example.com/epg.xml"), data.single().playlistEpgUrls)
        assertEquals(Channel.LICENSE_TYPE_CLEAR_KEY, data.single().licenseType)
        assertEquals("abc:def", data.single().licenseKey)
        assertEquals("CaseAgent/1.0", options[StreamUrlOptions.USER_AGENT])
        assertEquals("session=case", options[StreamUrlOptions.COOKIE])
    }

    @Test
    fun parseM3UPlaylistWithExtGrpFallbackGroup() = runBlocking {
        val input = """
            #EXTM3U
            #EXTINF:-1 tvg-id="extgrp",ExtGrp Channel
            #EXTGRP:Fallback Group
            https://example.com/extgrp.m3u8
            #EXTINF:-1 group-title="Metadata Group",Metadata Group Channel
            #EXTGRP:Ignored Group
            https://example.com/metadata-group.m3u8
        """.trimIndent()

        val data = parser.parse(input.byteInputStream()).toList()

        assertEquals(2, data.size)
        assertEquals("Fallback Group", data[0].group)
        assertEquals("ExtGrp Channel", data[0].title)
        assertEquals("Metadata Group", data[1].group)
        assertEquals("Metadata Group Channel", data[1].title)
    }

    @Test
    fun parseRealWorldGatherPlaylistSnippet() = runBlocking {
        val input = """
            #EXTM3U x-tvg-url="https://epg.yang-1989.eu.org/epg.xml.gz"
            #EXTINF:-1 tvg-id="免费订阅" tvg-name="免费订阅" tvg-logo="https://epg.yang-1989.eu.org/logo/温馨提示.png" group-title="•温馨「提示」",免费订阅：请勿贩卖...
            https://epg.yang-1989.eu.org/v/302.mp4
            #EXTINF:-1 tvg-id="咪咕体育" tvg-name="咪咕体育" tvg-logo="https://epg.yang-1989.eu.org/logo/咪咕.png" group-title="•咪咕「移动」",咪咕直播 𝟜𝕂-𝟙「移动」
            http://gslbserv.itv.cmvideo.cn:80/3000000010000005180/index.m3u8?channel-id=FifastbLive&Contentid=3000000010000005180&livemode=1&stbId=YanG-1989
        """.trimIndent()

        val data = parser.parse(input.byteInputStream()).toList()

        assertEquals(2, data.size)
        assertEquals("免费订阅", data[0].id)
        assertEquals("免费订阅：请勿贩卖...", data[0].title)
        assertEquals("•温馨「提示」", data[0].group)
        assertEquals("https://epg.yang-1989.eu.org/logo/温馨提示.png", data[0].cover)
        assertEquals("https://epg.yang-1989.eu.org/v/302.mp4", data[0].url)
        assertEquals("咪咕体育", data[1].id)
        assertEquals("咪咕直播 𝟜𝕂-𝟙「移动」", data[1].title)
        assertEquals("•咪咕「移动」", data[1].group)
    }

    @Test
    fun parsePlaylistHeaderEpgUrlsFromRealWorldFreeTvSnippet() = runBlocking {
        val input = """
            #EXTM3U x-tvg-url="https://epgshare01.online/epgshare01/epg_ripper_AL1.xml.gz, https://epgshare01.online/epgshare01/epg_ripper_US1.xml.gz"
            #EXTINF:-1 tvg-name="Kanali 7 Ⓢ" tvg-logo="https://i.imgur.com/rL2v9pM.png" tvg-id="Kanali7.al" tvg-country="AL" group-title="Albania",Kanali 7 Ⓢ
            https://fe.tring.al/delta/105/out/u/1200_1.m3u8
            #EXTINF:-1 tvg-name="A2 CNN Albania" tvg-logo="https://i.imgur.com/TgO3Lzi.png" tvg-id="A2CNN.al" tvg-country="AL" group-title="Albania",A2 CNN Albania
            https://tv.a2news.com/live/smil:a2cnnweb.stream.smil/playlist.m3u8
        """.trimIndent()

        val data = parser.parse(input.byteInputStream()).toList()

        assertEquals(2, data.size)
        assertEquals(
            listOf(
                "https://epgshare01.online/epgshare01/epg_ripper_AL1.xml.gz",
                "https://epgshare01.online/epgshare01/epg_ripper_US1.xml.gz"
            ),
            data.first().playlistEpgUrls
        )
        assertEquals(data.first().playlistEpgUrls, data[1].playlistEpgUrls)
        assertEquals("Kanali7.al", data[0].id)
        assertEquals("Kanali 7 Ⓢ", data[0].title)
        assertEquals("Albania", data[0].group)
    }

    @Test
    fun parsePlaylistHeaderEpgUrlsFromCommonAliases() = runBlocking {
        val input = """
            #EXTM3U tvg-url="https://example.com/tvg.xml" url-tvg="https://example.com/url-tvg.xml"
            #EXTINF:-1 tvg-id="news",News
            https://example.com/news.m3u8
        """.trimIndent()

        val data = parser.parse(input.byteInputStream()).toList()

        assertEquals(
            listOf(
                "https://example.com/url-tvg.xml",
                "https://example.com/tvg.xml"
            ),
            data.single().playlistEpgUrls
        )
    }

    @Test
    fun parseM3UPlaylistWithSingleQuotedMetadata() = runBlocking {
        val input = """
            #EXTM3U x-tvg-url = 'https://example.com/epg.xml'
            #EXTINF:-1 tvg-id = 'single-id' tvg-name='Single Name' tvg-logo='https://example.com/logo one.png' group-title='News Live',Single Quote Channel
            https://example.com/single-quote.m3u8
        """.trimIndent()

        val data = parser.parse(input.byteInputStream()).toList()

        assertEquals(1, data.size)
        assertEquals("single-id", data.single().id)
        assertEquals("Single Name", data.single().name)
        assertEquals("https://example.com/logo one.png", data.single().cover)
        assertEquals("News Live", data.single().group)
        assertEquals("Single Quote Channel", data.single().title)
        assertEquals(listOf("https://example.com/epg.xml"), data.single().playlistEpgUrls)
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

    @Test
    fun parseRelativeStreamUrlsAgainstPlaylistUrl() = runBlocking {
        val input = """
            #EXTM3U
            #EXTINF:-1 group-title="Live",Relative Channel
            streams/news.m3u8
            #EXTINF:-1 group-title="Live",Root Channel
            /live/root.m3u8
            #EXTINF:-1 group-title="Radio",Relative Camera
            audio/radio.mp3;video/weather.m3u8
        """.trimIndent()

        val data = parser.parse(input.byteInputStream()).toList()

        assertEquals(3, data.size)
        assertEquals(
            "https://example.com/lists/streams/news.m3u8",
            data[0].toChannel("https://example.com/lists/main.m3u").url
        )
        assertEquals(
            "https://example.com/live/root.m3u8",
            data[1].toChannel("https://example.com/lists/main.m3u").url
        )
        val channel = data[2].toChannel("https://example.com/lists/main.m3u")
        assertEquals(
            "https://example.com/lists/audio/radio.mp3",
            StreamUrlOptions.stripFromUrl(channel.url)
        )
        assertEquals(
            "https://example.com/lists/video/weather.m3u8",
            StreamUrlOptions.readFromUrl(channel.url)[StreamUrlOptions.VIDEO_URL]
        )
    }

    @Test
    fun parseSeparateAudioAndVideoStreamsDoesNotSplitSemicolonParameters() = runBlocking {
        val input = """
            #EXTM3U
            #EXTINF:-1 group-title="Live",Token Channel
            streams/news.m3u8;token=abc123
        """.trimIndent()

        val data = parser.parse(input.byteInputStream()).toList()
        val channel = data.single().toChannel("https://example.com/lists/main.m3u")

        assertEquals(
            "https://example.com/lists/streams/news.m3u8;token=abc123",
            channel.url
        )
        assertEquals(null, StreamUrlOptions.readFromUrl(channel.url)[StreamUrlOptions.VIDEO_URL])
    }

    @Test
    fun parseIssue371KodiClearKeyProperties() = runBlocking {
        val input = """
            #EXTM3U
            #EXTINF:-1 tvg-logo="",name1
            #EXTVLCOPT:http-user-agent=Android
            #KODIPROP:inputstreamaddon=inputstream.adaptive
            #KODIPROP:inputstream.adaptive.manifest_type=dash
            #KODIPROP:inputstream.adaptive.license_type=ClearKey
            #KODIPROP:inputstream.adaptive.license_key=a18b6aa739be4c0b114605fcfb5d6b68:b41c3a6f7511b2e3a828d9580124c89d
            https://example.com/single/index.mpd
            #EXTINF:-1 tvg-logo="",name2
            #EXTVLCOPT:http-user-agent=Android
            #KODIPROP:inputstreamaddon=inputstream.adaptive
            #KODIPROP:inputstream.adaptive.manifest_type=dash
            #KODIPROP:inputstream.adaptive.license_type=ORG.W3.CLEARKEY
            #KODIPROP:inputstream.adaptive.license_key={15965a6dbafd12c4af6aca127b271d5b:23dd40b93306de23ec667fb17a61f322,3decf356cc9351019fb1b627b089446d:4f7e516d3253d964e55b5c36f7f65d4a,511e929c12e0596bab59b11452de49a8:6f17d11eb6e069f4165bf48b425f9ea3}
            https://example.com/multi/index.mpd
        """.trimIndent()

        val data = parser.parse(input.byteInputStream()).toList()

        assertEquals(2, data.size)
        assertEquals(Channel.LICENSE_TYPE_CLEAR_KEY, data[0].licenseType)
        assertEquals(
            "a18b6aa739be4c0b114605fcfb5d6b68:b41c3a6f7511b2e3a828d9580124c89d",
            data[0].licenseKey
        )
        assertEquals(Channel.LICENSE_TYPE_CLEAR_KEY_2, data[1].licenseType)
        assertEquals(
            "{15965a6dbafd12c4af6aca127b271d5b:23dd40b93306de23ec667fb17a61f322," +
                "3decf356cc9351019fb1b627b089446d:4f7e516d3253d964e55b5c36f7f65d4a," +
                "511e929c12e0596bab59b11452de49a8:6f17d11eb6e069f4165bf48b425f9ea3}",
            data[1].licenseKey
        )
    }

    @Test
    fun parseVlcHttpOptions() = runBlocking {
        val input = """
            #EXTM3U
            #EXTINF:-1 group-title="Live",Header Channel
            #EXTVLCOPT:http-user-agent=MockAgent/1.0
            #EXTVLCOPT:http-referrer=https://referrer.example.com/
            #EXTVLCOPT:http-origin=https://origin.example.com
            https://example.com/header.m3u8
        """.trimIndent()

        val data = parser.parse(input.byteInputStream()).toList()
        val channel = data.single().toChannel("https://playlist.example.com/list.m3u")
        val options = StreamUrlOptions.readFromUrl(channel.url)

        assertEquals("MockAgent/1.0", options[StreamUrlOptions.USER_AGENT])
        assertEquals("https://referrer.example.com/", options[StreamUrlOptions.REFERER])
        assertEquals("https://origin.example.com", options[StreamUrlOptions.ORIGIN])
    }

    @Test
    fun parseVlcCustomHttpOptionsAsRequestHeaders() = runBlocking {
        val input = """
            #EXTM3U
            #EXTINF:-1 group-title="Live",Custom Header Channel
            #EXTVLCOPT:http-x-api-key=secret
            #EXTVLCOPT:http-authorization=Bearer token
            #EXTVLCOPT:http-user-agent=MockAgent/4.0
            https://example.com/custom-header.m3u8
        """.trimIndent()

        val data = parser.parse(input.byteInputStream()).toList()
        val channel = data.single().toChannel("https://playlist.example.com/list.m3u")
        val headers = StreamUrlOptions.readRequestHeadersFromUrl(channel.url)
        val options = StreamUrlOptions.readFromUrl(channel.url)

        assertEquals("secret", headers["x-api-key"])
        assertEquals("Bearer token", headers["authorization"])
        assertEquals("MockAgent/4.0", options[StreamUrlOptions.USER_AGENT])
        assertEquals(null, headers["user-agent"])
    }

    @Test
    fun parseExtHttpCookie() = runBlocking {
        val input = """
            #EXTM3U
            #EXTINF:-1 group-title="Live",Cookie Channel
            #EXTHTTP:{"cookie":"Edge-Cache-Cookie=URLPrefix=abc:Expires=1750297799:Signature=xyz"}
            https://example.com/cookie.m3u8
        """.trimIndent()

        val data = parser.parse(input.byteInputStream()).toList()
        val channel = data.single().toChannel("https://playlist.example.com/list.m3u")
        val options = StreamUrlOptions.readFromUrl(channel.url)

        assertEquals(
            "Edge-Cache-Cookie=URLPrefix=abc:Expires=1750297799:Signature=xyz",
            options[StreamUrlOptions.COOKIE]
        )
    }

    @Test
    fun normalizePipeHttpOptionKeys() {
        val url = StreamUrlOptions.appendToUrl(
            "https://example.com/stream.mpd",
            mapOf(
                "User-Agent" to "MockAgent/2.0",
                "Referer" to "https://referrer.example.com/",
                "http-origin" to "https://origin.example.com",
                "http-cookie" to "session=abc"
            )
        )

        val options = StreamUrlOptions.readFromUrl(url)

        assertEquals("MockAgent/2.0", options[StreamUrlOptions.USER_AGENT])
        assertEquals("https://referrer.example.com/", options[StreamUrlOptions.REFERER])
        assertEquals("https://origin.example.com", options[StreamUrlOptions.ORIGIN])
        assertEquals("session=abc", options[StreamUrlOptions.COOKIE])
    }

    @Test
    fun readRequestHeadersKeepsCustomHttpOptions() {
        val url = StreamUrlOptions.appendToUrl(
            "https://example.com/stream.mpd",
            mapOf(
                "user-agent" to "MockAgent/3.0",
                "referer" to "https://referrer.example.com/",
                "origin" to "https://origin.example.com",
                "cookie" to "session=abc",
                "x-api-key" to "secret",
                "video-url" to "https://example.com/video.m3u8"
            )
        )

        val headers = StreamUrlOptions.readRequestHeadersFromUrl(url)

        assertEquals(4, headers.size)
        assertEquals("https://referrer.example.com/", headers["Referer"])
        assertEquals("https://origin.example.com", headers["Origin"])
        assertEquals("session=abc", headers["Cookie"])
        assertEquals("secret", headers["x-api-key"])
    }

    @Test
    fun readEncodedPipeHttpOptionsFromUrl() {
        val url =
            "https://example.com/index.mpd?id=SONY-PIX%7Ccookie%3DEdge-Cache-Cookie%3Dabc&User-Agent=MockAgent/5.0"

        val options = StreamUrlOptions.readFromUrl(url)
        val headers = StreamUrlOptions.readRequestHeadersFromUrl(url)

        assertEquals("SONY-PIX", StreamUrlOptions.stripFromUrl(url).substringAfter("id="))
        assertEquals("MockAgent/5.0", options[StreamUrlOptions.USER_AGENT])
        assertEquals("Edge-Cache-Cookie=abc", options[StreamUrlOptions.COOKIE])
        assertEquals("Edge-Cache-Cookie=abc", headers["Cookie"])
    }

    @Test
    fun appendVlcOptionsAfterEncodedPipeHttpOptions() = runBlocking {
        val input = """
            #EXTM3U
            #EXTINF:-1 group-title="Live",Encoded Cookie Channel
            #EXTVLCOPT:http-user-agent=MockAgent/6.0
            https://example.com/index.mpd?id=SONY-PIX%7Ccookie%3DEdge-Cache-Cookie%3Dabc
        """.trimIndent()

        val data = parser.parse(input.byteInputStream()).toList()
        val channel = data.single().toChannel("https://playlist.example.com/list.m3u")
        val options = StreamUrlOptions.readFromUrl(channel.url)
        val headers = StreamUrlOptions.readRequestHeadersFromUrl(channel.url)

        assertEquals("https://example.com/index.mpd?id=SONY-PIX", StreamUrlOptions.stripFromUrl(channel.url))
        assertEquals("MockAgent/6.0", options[StreamUrlOptions.USER_AGENT])
        assertEquals("Edge-Cache-Cookie=abc", options[StreamUrlOptions.COOKIE])
        assertEquals("Edge-Cache-Cookie=abc", headers["Cookie"])
    }
}
