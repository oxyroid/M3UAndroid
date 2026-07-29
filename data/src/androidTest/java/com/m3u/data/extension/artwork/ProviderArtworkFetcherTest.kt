package com.m3u.data.extension.artwork

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil.ImageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.m3u.data.database.model.ProviderAccount
import com.m3u.data.extension.emby.EmbyCompatibleProvider
import com.m3u.extension.api.subscription.EmbyCompatibleProviderKinds
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderArtworkFetcherTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun embyAndJellyfinArtworkUsesHeaderAuthenticationWithoutPersistingSecrets() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val accounts = listOf(
                providerAccount(
                    id = "emby-account",
                    providerKind = EmbyCompatibleProviderKinds.Emby.value,
                    baseUrl = server.url("/emby").toString(),
                ),
                providerAccount(
                    id = "jellyfin-account",
                    providerKind = EmbyCompatibleProviderKinds.Jellyfin.value,
                    baseUrl = server.url("/jellyfin").toString(),
                ),
            )
            val accessByAccount = accounts.associate { account ->
                account.id to ProviderArtworkAccess(
                    baseUrl = account.baseUrl,
                    providerKind = account.providerKind,
                    accessToken = ACCESS_TOKEN,
                )
            }
            val imageLoader = imageLoader { accountId -> accessByAccount[accountId] }
            try {
                accounts.forEachIndexed { index, account ->
                    val imageTag = "poster-$index-v1"
                    val remoteUrl = server.url(
                        "/${account.providerKind}/Items/item-$index/Images/Primary"
                    )
                        .newBuilder()
                        .addQueryParameter(PROVIDER_ARTWORK_TAG_QUERY, imageTag)
                        .build()
                        .toString()
                    val reference = checkNotNull(
                        ProviderArtworkReferences.create(account, remoteUrl)
                    )
                    assertFalse(reference.contains(ACCESS_TOKEN))
                    assertFalse(reference.contains(remoteUrl))
                    server.enqueue(
                        MockResponse.Builder()
                            .code(200)
                            .addHeader("Content-Type", "image/png")
                            .body(Buffer().write(PNG_BYTES))
                            .build()
                    )

                    val result = imageLoader.execute(
                        ImageRequest.Builder(context)
                            .data(reference)
                            .build()
                    )

                    assertTrue(
                        result.failureDetails(),
                        result is SuccessResult,
                    )
                    val recordedRequest = server.takeRequest(1, TimeUnit.SECONDS)
                    assertNotNull(recordedRequest)
                    val request = checkNotNull(recordedRequest)
                    when (account.providerKind) {
                        EmbyCompatibleProviderKinds.Emby.value -> {
                            assertEquals(ACCESS_TOKEN, request.headers["X-Emby-Token"])
                            assertNull(request.headers["Authorization"])
                        }

                        EmbyCompatibleProviderKinds.Jellyfin.value -> {
                            assertEquals(
                                "MediaBrowser Token=\"$ACCESS_TOKEN\"",
                                request.headers["Authorization"],
                            )
                            assertNull(request.headers["X-Emby-Token"])
                        }
                    }
                    assertFalse(request.url.toString().contains("api_key"))
                    assertEquals(
                        imageTag,
                        request.url.queryParameter(PROVIDER_ARTWORK_TAG_QUERY),
                    )
                    assertEquals(
                        "/${account.providerKind}/Items/item-$index/Images/Primary",
                        request.url.encodedPath,
                    )
                }
            } finally {
                imageLoader.shutdown()
            }
        } finally {
            server.close()
        }
    }

    @Test
    fun artworkCredentialIsNotForwardedAcrossOriginRedirect() = runBlocking {
        val accountServer = MockWebServer()
        val foreignServer = MockWebServer()
        accountServer.start()
        foreignServer.start()
        try {
            val account = providerAccount(
                id = "redirect-account",
                providerKind = EmbyCompatibleProviderKinds.Emby.value,
                baseUrl = accountServer.url("/").toString(),
            )
            accountServer.enqueue(
                MockResponse(
                    code = 302,
                    headers = headersOf(
                        "Location",
                        foreignServer.url("/stolen.png").toString(),
                    ),
                )
            )
            val accessByAccount = mapOf(
                account.id to ProviderArtworkAccess(
                    baseUrl = account.baseUrl,
                    providerKind = account.providerKind,
                    accessToken = ACCESS_TOKEN,
                )
            )
            val imageLoader = imageLoader { accountId -> accessByAccount[accountId] }
            try {
                val reference = checkNotNull(
                    ProviderArtworkReferences.create(
                        account = account,
                        remoteUrl = accountServer.url("/Items/item/Images/Primary")
                            .newBuilder()
                            .addQueryParameter(PROVIDER_ARTWORK_TAG_QUERY, "redirect-v1")
                            .build()
                            .toString(),
                    )
                )

                val result = imageLoader.execute(
                    ImageRequest.Builder(context)
                        .data(reference)
                        .build()
                )

                assertTrue(result is ErrorResult)
                assertEquals(
                    result.failureDetails(),
                    1,
                    accountServer.requestCount,
                )
                assertEquals(0, foreignServer.requestCount)
                assertEquals(
                    ACCESS_TOKEN,
                    accountServer.takeRequest().headers["X-Emby-Token"],
                )
            } finally {
                imageLoader.shutdown()
            }
        } finally {
            accountServer.close()
            foreignServer.close()
        }
    }

    @Test
    fun oversizedArtworkResponseIsRejected() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val account = providerAccount(
                id = "oversized-account",
                providerKind = EmbyCompatibleProviderKinds.Jellyfin.value,
                baseUrl = server.url("/").toString(),
            )
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "image/jpeg")
                    .body(Buffer().write(ByteArray(MAX_ARTWORK_RESPONSE_BYTES + 1)))
                    .build()
            )
            val accessByAccount = mapOf(
                account.id to ProviderArtworkAccess(
                    baseUrl = account.baseUrl,
                    providerKind = account.providerKind,
                    accessToken = ACCESS_TOKEN,
                )
            )
            val imageLoader = imageLoader { accountId -> accessByAccount[accountId] }
            try {
                val reference = checkNotNull(
                    ProviderArtworkReferences.create(
                        account = account,
                        remoteUrl = server.url("/Items/item/Images/Primary")
                            .newBuilder()
                            .addQueryParameter(PROVIDER_ARTWORK_TAG_QUERY, "oversized-v1")
                            .build()
                            .toString(),
                    )
                )

                val result = imageLoader.execute(
                    ImageRequest.Builder(context)
                        .data(reference)
                        .build()
                )

                assertTrue(result is ErrorResult)
                val error = (result as ErrorResult).throwable
                assertTrue(
                    result.failureDetails(),
                    generateSequence(error) { cause -> cause.cause }
                        .any { cause ->
                            cause.message.orEmpty().contains("exceeds the host limit")
                        }
                )
            } finally {
                imageLoader.shutdown()
            }
        } finally {
            server.close()
        }
    }

    @Test
    fun crossOriginArtworkCannotBecomeAHostReference() {
        val account = providerAccount(
            id = "origin-account",
            providerKind = EmbyCompatibleProviderKinds.Emby.value,
            baseUrl = "https://media.example.test/emby",
        )

        val failure = runCatching {
            ProviderArtworkReferences.create(
                account = account,
                remoteUrl = "https://foreign.example.test/poster.png?tag=foreign-v1",
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun importerArtworkValidationAcceptsOnlySameOriginStableImageTags() {
        val baseUrl = "https://media.example.test/emby"

        assertTrue(
            ProviderArtworkReferences.isValidRemoteUrl(
                baseUrl = baseUrl,
                remoteUrl =
                    "https://media.example.test/emby/Items/item/Images/Primary?tag=poster-v1",
            )
        )
        assertFalse(
            ProviderArtworkReferences.isValidRemoteUrl(
                baseUrl = baseUrl,
                remoteUrl =
                    "https://media.example.test/emby/Items/item/Images/Primary?tag=poster-v1&x=1",
            )
        )
        assertFalse(
            ProviderArtworkReferences.isValidRemoteUrl(
                baseUrl = baseUrl,
                remoteUrl = "https://media.example.test/emby/Items/item/Images/Primary",
            )
        )
        assertFalse(
            ProviderArtworkReferences.isValidRemoteUrl(
                baseUrl = baseUrl,
                remoteUrl =
                    "https://foreign.example.test/emby/Items/item/Images/Primary?tag=poster-v1",
            )
        )
    }

    @Test
    fun imageTagChangesTheOpaqueReferenceAndCacheKey() {
        val account = providerAccount(
            id = "cache-account",
            providerKind = EmbyCompatibleProviderKinds.Emby.value,
            baseUrl = "https://media.example.test/emby",
        )
        val first = checkNotNull(
            ProviderArtworkReferences.create(
                account = account,
                remoteUrl =
                    "https://media.example.test/emby/Items/item/Images/Primary?tag=poster-v1",
            )
        )
        val second = checkNotNull(
            ProviderArtworkReferences.create(
                account = account,
                remoteUrl =
                    "https://media.example.test/emby/Items/item/Images/Primary?tag=poster-v2",
            )
        )

        assertNotEquals(first, second)
        assertEquals("poster-v1", ProviderArtworkReferences.parse(first)?.imageTag)
        assertEquals("poster-v2", ProviderArtworkReferences.parse(second)?.imageTag)
        assertFalse(first.contains("poster-v1"))
        assertFalse(second.contains("poster-v2"))
    }

    private fun imageLoader(
        resolve: suspend (String) -> ProviderArtworkAccess?,
    ): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(
                ProviderArtworkFetcherFactory(
                    accessResolver = ProviderArtworkAccessResolver(resolve),
                    okHttpClient = OkHttpClient.Builder()
                        .proxy(Proxy.NO_PROXY)
                        .build(),
                )
            )
        }
        .build()

    private fun Any.failureDetails(): String = when (this) {
        is ErrorResult -> throwable.stackTraceToString()
        else -> toString()
    }

    private fun providerAccount(
        id: String,
        providerKind: String,
        baseUrl: String,
    ) = ProviderAccount(
        id = id,
        providerId = EmbyCompatibleProvider.ID.value,
        providerKind = providerKind,
        baseUrl = baseUrl,
        serverId = "server-$id",
        serverName = "Server",
        serverVersion = "1",
        userId = "user-$id",
        username = "viewer",
        playlistUrl = "m3u-provider://account/$id/live",
    )

    private companion object {
        const val ACCESS_TOKEN = "artwork-secret-token"
        val PNG_BYTES: ByteArray = Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
            Base64.DEFAULT,
        )
    }
}
