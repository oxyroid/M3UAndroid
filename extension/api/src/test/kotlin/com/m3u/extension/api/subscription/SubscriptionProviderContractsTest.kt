package com.m3u.extension.api.subscription

import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.security.CredentialHandle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionProviderContractsTest {
    @Test
    fun `compatible provider exposes distinct user facing kinds`() {
        assertEquals("emby", EmbyCompatibleProviderKinds.Emby.value)
        assertEquals("jellyfin", EmbyCompatibleProviderKinds.Jellyfin.value)
    }

    @Test
    fun `provider kind rejects non portable identifier`() {
        assertFailsWith<IllegalArgumentException> { ProviderKind("Emby Server") }
    }

    @Test
    fun `provider media kinds keep known values without closing the wire vocabulary`() {
        assertEquals("unknown", ProviderMediaKinds.Unknown.value)
        assertEquals("live", ProviderMediaKinds.Live.value)
        assertEquals("movie", ProviderMediaKinds.Movie.value)
        assertEquals("series", ProviderMediaKinds.Series.value)
        assertEquals("season", ProviderMediaKinds.Season.value)
        assertEquals("episode", ProviderMediaKinds.Episode.value)
        assertEquals("audiobook", ProviderMediaKind("audiobook").value)

        assertFailsWith<IllegalArgumentException> {
            ProviderMediaKind("Movie")
        }
        assertFailsWith<IllegalArgumentException> {
            ProviderMediaKind("a".repeat(ProviderMediaKind.MAX_LENGTH + 1))
        }
    }

    @Test
    fun `browse request bounds its opaque cursor and page size`() {
        val request = SubscriptionContentBrowseRequest(
            account = account(),
            credential = credential(),
            parentReference = reference("series-1", "series"),
            cursor = "page-2",
            limit = SubscriptionContentBrowseRequest.MAX_LIMIT,
        )

        assertEquals("series-1", request.parentReference?.itemId)
        assertEquals("page-2", request.cursor)
        assertFailsWith<IllegalArgumentException> {
            request.copy(cursor = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            request.copy(
                cursor = "界".repeat(
                    SubscriptionContentBrowseRequest.MAX_CURSOR_UTF8_BYTES / 3 + 1
                )
            )
        }
        assertFailsWith<IllegalArgumentException> {
            request.copy(limit = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            request.copy(limit = SubscriptionContentBrowseRequest.MAX_LIMIT + 1)
        }
    }

    @Test
    fun `browse items remain referenceable when they are only browsable`() {
        val series = item(
            reference = reference("series-1", "series"),
            mediaKind = ProviderMediaKinds.Series,
            playable = false,
            browsable = true,
        )

        assertFalse(series.playable)
        assertTrue(series.browsable)
        assertEquals("series-1", series.reference.itemId)
        assertFailsWith<IllegalArgumentException> {
            series.copy(playable = false, browsable = false)
        }
        assertFailsWith<IllegalArgumentException> {
            series.copy(title = "")
        }
        assertFailsWith<IllegalArgumentException> {
            series.copy(
                title = "界".repeat(
                    SubscriptionContentItemDescriptor.MAX_TITLE_UTF8_BYTES / 3 + 1
                )
            )
        }
        assertFailsWith<IllegalArgumentException> {
            series.copy(imageUrl = "")
        }
        assertFailsWith<IllegalArgumentException> {
            series.copy(category = "")
        }
        assertFailsWith<IllegalArgumentException> {
            series.copy(subtitle = "")
        }
        assertFailsWith<IllegalArgumentException> {
            series.copy(
                overview = "界".repeat(
                    SubscriptionContentItemDescriptor.MAX_OVERVIEW_UTF8_BYTES / 3 + 1
                )
            )
        }
        assertFailsWith<IllegalArgumentException> {
            series.copy(productionYear = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            series.copy(seasonNumber = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            series.copy(
                episodeNumber = SubscriptionContentItemDescriptor.MAX_INDEX_NUMBER + 1
            )
        }
    }

    @Test
    fun `browse result bounds page entries cursor and total`() {
        val descriptor = item(
            reference = reference("movie-1", "movie"),
            mediaKind = ProviderMediaKinds.Movie,
            playable = true,
            browsable = false,
        )
        val result = SubscriptionContentBrowseResult(
            items = listOf(descriptor),
            nextCursor = "page-2",
            total = 2,
        )

        assertEquals(1, result.items.size)
        assertFailsWith<IllegalArgumentException> {
            result.copy(
                items = List(SubscriptionContentBrowseResult.MAX_ITEMS_PER_PAGE + 1) {
                    descriptor
                }
            )
        }
        assertFailsWith<IllegalArgumentException> {
            result.copy(nextCursor = "")
        }
        assertFailsWith<IllegalArgumentException> {
            result.copy(total = -1)
        }
    }

    @Test
    fun `playback progress uses extensible events and bounded positions`() {
        assertEquals("started", PlaybackSessionEvents.Started.value)
        assertEquals("progress", PlaybackSessionEvents.Progress.value)
        assertEquals("paused", PlaybackSessionEvents.Paused.value)
        assertEquals("resumed", PlaybackSessionEvents.Resumed.value)
        assertEquals("buffering", PlaybackSessionEvent("buffering").value)
        assertEquals("direct_play", PlaybackMethods.DirectPlay.value)
        assertEquals("remote_direct", PlaybackMethod("remote_direct").value)
        assertFailsWith<IllegalArgumentException> {
            PlaybackSessionEvent("Paused")
        }
        assertFailsWith<IllegalArgumentException> {
            PlaybackMethod("")
        }

        val request = PlaybackSessionUpdateRequest(
            account = account(),
            credential = credential(),
            reference = reference("movie-1", "movie"),
            session = PlaybackSessionDescriptor(playSessionId = "session-1"),
            event = PlaybackSessionEvents.Progress,
            positionTicks = 90_000_000L,
            playMethod = PlaybackMethods.DirectPlay,
            isPaused = false,
        )
        assertEquals(90_000_000L, request.positionTicks)
        assertFailsWith<IllegalArgumentException> {
            request.copy(positionTicks = -1L)
        }
    }

    @Test
    fun `playback lifecycle requires current method and position fields`() {
        assertEquals(
            PlaybackMethods.DirectPlay,
            PlaybackSourceResolveResult(
                url = "https://media.example.test/item",
                playMethod = PlaybackMethods.DirectPlay,
            ).playMethod,
        )
        val close = PlaybackSessionCloseRequest(
            account = account(),
            credential = credential(),
            reference = reference("movie-1", "movie"),
            session = PlaybackSessionDescriptor(playSessionId = "session-1"),
            reason = PlaybackSessionCloseReason.Stopped,
            positionTicks = 90_000_000L,
        )

        assertEquals(90_000_000L, close.positionTicks)
        assertFailsWith<IllegalArgumentException> {
            close.copy(positionTicks = -1L)
        }
    }

    private fun account() = ProviderAccountReference(
        accountId = "account-1",
        providerId = ExtensionId("com.example.provider"),
        providerKind = ProviderKind("example"),
        baseUrl = "https://media.example.test",
        serverId = "server-1",
        serverName = "Example Server",
        serverVersion = "1.0",
        userId = "user-1",
        username = "alex",
    )

    private fun credential() = ProviderCredential(CredentialHandle("credential-1"))

    private fun reference(
        itemId: String,
        sourceType: String,
    ) = PlaybackReference(
        providerId = ExtensionId("com.example.provider"),
        itemId = itemId,
        sourceType = sourceType,
    )

    private fun item(
        reference: PlaybackReference,
        mediaKind: ProviderMediaKind,
        playable: Boolean,
        browsable: Boolean,
    ) = SubscriptionContentItemDescriptor(
        reference = reference,
        mediaKind = mediaKind,
        title = "Example item",
        playable = playable,
        browsable = browsable,
        imageUrl = "https://media.example.test/items/${reference.itemId}/image",
        category = "Example",
        subtitle = "2026 · Example",
        overview = "A bounded description for the example item.",
        productionYear = 2026,
    )
}
