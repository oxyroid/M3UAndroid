package com.m3u.data.extension.emby

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.m3u.core.foundation.architecture.Abi
import com.m3u.core.foundation.architecture.Publisher
import com.m3u.extension.api.subscription.EmbyCompatibleProviderKinds
import com.m3u.extension.api.subscription.PlaybackPreferences
import com.m3u.extension.api.subscription.ProviderKind
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmbyCompatibleProviderIntegrationTest {
    @Test
    fun embyAndJellyfinCompletePlaybackLifecycleAgainstMockServer() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val okHttpClient = OkHttpClient()
        val client = OkHttpEmbyCompatibleClient(
            context = context,
            publisher = TestPublisher,
            okHttpClient = okHttpClient,
        )
        val serverUrl = InstrumentationRegistry.getArguments()
            .getString("m3uMockServerUrl")
            .orEmpty()
            .ifBlank { "http://10.0.2.2:8080" }

        exerciseLifecycle(
            client = client,
            okHttpClient = okHttpClient,
            serverUrl = serverUrl,
            requestedKind = EmbyCompatibleProviderKinds.Emby,
        )
        exerciseLifecycle(
            client = client,
            okHttpClient = okHttpClient,
            serverUrl = "$serverUrl/jellyfin",
            requestedKind = EmbyCompatibleProviderKinds.Jellyfin,
        )
    }

    private suspend fun exerciseLifecycle(
        client: EmbyCompatibleClient,
        okHttpClient: OkHttpClient,
        serverUrl: String,
        requestedKind: ProviderKind,
    ) {
        val validation = client.validate(
            baseUrl = serverUrl,
            requestedKind = requestedKind,
            username = "m3u",
            password = "m3u",
        )
        assertEquals(requestedKind, validation.account.detectedKind)
        assertEquals("mock-server-id", validation.account.serverId)
        assertTrue(validation.accessToken.isNotBlank())

        val refresh = client.refreshChannels(validation.account, validation.accessToken)
        assertEquals(2, refresh.channels.size)
        assertEquals(setOf("mock.news", "mock.sports"), refresh.channels.map { it.remoteId }.toSet())

        val reference = refresh.channels.first().playbackReference
        val playback = client.resolvePlayback(
            account = validation.account,
            accessToken = validation.accessToken,
            reference = reference,
            preferences = PlaybackPreferences(),
        )
        assertFalse(playback.url.contains(validation.accessToken))
        assertEquals("allowed", playback.headers["X-Mock-Playback"])
        assertNotNull(playback.session)

        val request = Request.Builder()
            .url(playback.url)
            .apply {
                playback.headers.forEach { (name, value) -> header(name, value) }
            }
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            assertTrue(response.isSuccessful)
            assertTrue(response.body.string().startsWith("#EXTM3U"))
        }

        assertTrue(
            client.closePlayback(
                account = validation.account,
                accessToken = validation.accessToken,
                reference = reference.copy(mediaSourceId = playback.mediaSourceId),
                session = requireNotNull(playback.session),
            )
        )
    }

    private data object TestPublisher : Publisher {
        override val applicationId = "com.m3u.data.test"
        override val versionName = "test"
        override val versionCode = 1
        override val debug = true
        override val model = "Android test"
        override val abi = Abi.x86_64
    }
}
