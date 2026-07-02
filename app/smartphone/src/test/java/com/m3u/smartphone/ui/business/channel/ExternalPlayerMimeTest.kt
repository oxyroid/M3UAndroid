package com.m3u.smartphone.ui.business.channel

import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalPlayerMimeTest {
    @Test
    fun returnsAudioMimeTypeForAudioStreams() {
        assertEquals(
            "audio/mpeg",
            externalPlayerMimeType("https://example.com/live/station.mp3")
        )
        assertEquals(
            "audio/aac",
            externalPlayerMimeType("https://example.com/radio/stream.aac")
        )
        assertEquals(
            "audio/ogg",
            externalPlayerMimeType("https://example.com/radio/stream.opus")
        )
    }

    @Test
    fun ignoresQueryFragmentAndExtensionCase() {
        assertEquals(
            "audio/flac",
            externalPlayerMimeType("https://example.com/radio/STREAM.FLAC?token=abc#live")
        )
    }

    @Test
    fun keepsVideoFallbackForUnknownStreams() {
        assertEquals(
            "video/*",
            externalPlayerMimeType("https://example.com/live/channel.ts?token=abc")
        )
        assertEquals(
            "video/*",
            externalPlayerMimeType("https://example.com/live/channel")
        )
    }
}
