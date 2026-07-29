package com.m3u.data.service.internal

import com.m3u.data.database.model.SeriesEpisode
import com.m3u.data.database.model.SeriesEpisodeSource
import com.m3u.data.service.MediaCommand
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.subscription.PlaybackReference
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackLogSafetyTest {
    @Test
    fun mediaCommandSummaryDoesNotExpandExtensionControlledEpisodeData() {
        val secret = "provider-secret"
        val command = MediaCommand.Episode(
            channelId = 42,
            episode = SeriesEpisode(
                id = "episode-$secret",
                title = "title-$secret",
                artworkUrl = "https://media.example/image?api_key=$secret",
                source = SeriesEpisodeSource.Provider(
                    reference = PlaybackReference(
                        providerId = ExtensionId("example.provider"),
                        itemId = "item-$secret",
                        sourceType = "episode",
                    )
                ),
            ),
        )

        val summary = command.toPlaybackLogSummary()

        assertEquals("Episode[channelId=42]", summary)
        assertFalse(summary.contains(secret))
        assertFalse(summary.contains("https://"))
    }

    @Test
    fun playbackChainSummaryHasNoPlaceForAPlaybackUrl() {
        assertEquals(
            "Trying[mimeType=application/x-mpegURL]",
            playbackChainLogSummary(
                state = "Trying",
                mimeType = "application/x-mpegURL",
            ),
        )
    }

    @Test
    fun logThrowablePreservesDiagnosticsWithoutMessagesOrSecrets() {
        val secret = "api-token-value"
        val original = IllegalStateException(
            "GET https://media.example/video?api_key=$secret",
            IOException("Authorization: Bearer $secret"),
        ).apply {
            addSuppressed(
                IllegalArgumentException("X-Emby-Token: $secret")
            )
        }
        val originalTopFrame = original.stackTrace.first()

        val safe = original.toPlaybackLogThrowable()
        val rendered = StringWriter().also { writer ->
            safe.printStackTrace(PrintWriter(writer))
        }.toString()

        assertTrue(rendered.contains(IllegalStateException::class.java.name))
        assertTrue(rendered.contains(IOException::class.java.name))
        assertTrue(rendered.contains(IllegalArgumentException::class.java.name))
        assertTrue(safe.stackTrace.first() == originalTopFrame)
        assertFalse(rendered.contains(secret))
        assertFalse(rendered.contains("https://"))
        assertFalse(rendered.contains("Authorization:"))
        assertFalse(rendered.contains("X-Emby-Token:"))
        assertFalse(rendered.contains("api_key"))
    }
}
