package com.m3u.testing

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.datastore.preferences.core.edit
import androidx.media3.common.Player
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.settings
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.DataSource
import com.m3u.data.repository.playlist.PlaylistRefreshReason
import com.m3u.data.repository.plugin.PluginEnableResult
import com.m3u.data.repository.provider.ProviderOperationException
import com.m3u.data.repository.provider.ProviderPlaybackCloseReason
import com.m3u.data.repository.provider.ProviderPlaybackSession
import com.m3u.data.repository.provider.ProviderSubscriptionRequest
import com.m3u.data.service.MediaCommand
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionState
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.api.subscription.SubscriptionProviderSettingKeys
import com.m3u.smartphone.DebugExtensionPlatformEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalProviderEndToEndTest {
    @Test
    fun referenceProviderRefreshesAndPlaysThroughProductServicesAcrossBinder() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext.applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            DebugExtensionPlatformEntryPoint::class.java,
        )
        val pluginRepository = entryPoint.pluginRepository()
        val providerRepository = entryPoint.providerRepository()
        val playlistRepository = entryPoint.playlistRepository()
        val playerManager = entryPoint.playerManager()
        val workManager = WorkManager.getInstance(context)
        var pluginPackage: String? = null
        var pluginService: String? = null
        var providerPlaylistUrl: String? = null
        var activeSession: ProviderPlaybackSession? = null
        var serverUrl: String? = null

        try {
            context.settings.edit { preferences ->
                preferences[PreferencesKeys.EXTERNAL_EXTENSIONS] = true
            }
            val plugin = pluginRepository.installedPlugins().single { installed ->
                installed.packageName == REFERENCE_PACKAGE
            }
            pluginPackage = plugin.packageName
            pluginService = plugin.serviceName
            val enableResult = pluginRepository.enable(
                plugin.packageName,
                plugin.serviceName,
                checkNotNull(plugin.authorizationToken),
            )
            assertTrue(
                "Reference provider could not be enabled: $enableResult",
                enableResult is PluginEnableResult.Enabled,
            )

            playlistRepository.getBySource(DataSource.Provider)
                .filter { playlist -> playlist.title == PLAYLIST_TITLE }
                .forEach { playlist -> playlistRepository.unsubscribe(playlist.url) }

            val descriptor = providerRepository.discoverProviders().single { provider ->
                provider.descriptor.providerId == REFERENCE_EXTENSION_ID
            }.descriptor
            assertTrue(descriptor.variants.any { variant -> variant.kind == REFERENCE_PROVIDER_KIND })
            val currentServerUrl = InstrumentationRegistry.getArguments()
                .getString("m3uMockServerUrl", DEFAULT_SERVER_URL)
                .trimEnd('/')
            serverUrl = currentServerUrl
            assertMockServerReady(currentServerUrl)
            repeat(3) {
                val failure = runCatching {
                    providerRepository.subscribe(
                        ProviderSubscriptionRequest(
                            title = PLAYLIST_TITLE,
                            providerId = REFERENCE_EXTENSION_ID,
                            providerKind = REFERENCE_PROVIDER_KIND,
                            settingValues = mapOf(
                                SubscriptionProviderSettingKeys.BaseUrl to currentServerUrl,
                                SubscriptionProviderSettingKeys.Username to "m3u",
                            ),
                            credentialHandles = mapOf(
                                SubscriptionProviderSettingKeys.Password to
                                    providerRepository.stageCredential("wrong-password"),
                            ),
                        )
                    )
                }.exceptionOrNull()
                assertTrue(failure is ProviderOperationException)
                assertEquals(
                    "provider.authentication_failed",
                    (failure as ProviderOperationException).code,
                )
            }
            val pluginAfterRejectedLogins = pluginRepository.installedPlugins().single { installed ->
                installed.packageName == REFERENCE_PACKAGE
            }
            assertEquals(ExtensionState.ENABLED, pluginAfterRejectedLogins.state)

            val subscription = providerRepository.subscribe(
                ProviderSubscriptionRequest(
                    title = PLAYLIST_TITLE,
                    providerId = REFERENCE_EXTENSION_ID,
                    providerKind = REFERENCE_PROVIDER_KIND,
                    settingValues = mapOf(
                        SubscriptionProviderSettingKeys.BaseUrl to currentServerUrl,
                        SubscriptionProviderSettingKeys.Username to "m3u",
                    ),
                    credentialHandles = mapOf(
                        SubscriptionProviderSettingKeys.Password to
                            providerRepository.stageCredential("reference-password"),
                    ),
                )
            )
            providerPlaylistUrl = subscription.playlistUrl
            assertEquals(2, subscription.channelCount)
            assertEquals(
                2,
                requireNotNull(
                    playlistRepository.getPlaylistWithChannels(subscription.playlistUrl)
                ).channels.size,
            )

            assertWorkManagerNetworkReady(context)
            val refreshWorkId = requireNotNull(
                playlistRepository.refreshWithWorkId(
                    url = subscription.playlistUrl,
                    reason = PlaylistRefreshReason.BACKGROUND,
                )
            )
            val refreshWork = withTimeout(WORK_TIMEOUT_MILLIS) {
                workManager.getWorkInfoByIdFlow(refreshWorkId)
                    .filterNotNull()
                    .first { workInfo -> workInfo.state.isFinished }
            }
            assertEquals(WorkInfo.State.SUCCEEDED, refreshWork.state)
            assertEquals(
                2,
                refreshWork.outputData.getInt(OUTPUT_CHANNEL_COUNT_KEY, -1),
            )
            val channels = requireNotNull(
                playlistRepository.getPlaylistWithChannels(subscription.playlistUrl)
            ).channels
            assertEquals(2, channels.size)
            val news = channels.single { channel -> channel.relationId == REFERENCE_NEWS_ID }
            assertEquals(Channel.URL_DYNAMIC, news.url)

            val source = requireNotNull(providerRepository.resolvePlayback(news.id))
            assertNotEquals(Channel.URL_DYNAMIC, source.url)
            assertEquals(
                "$currentServerUrl/reference-provider/stream/$REFERENCE_NEWS_ID/sample.wav",
                source.url,
            )
            assertEquals(REFERENCE_ACCESS_TOKEN, source.headers["X-Emby-Token"])
            assertEquals(REFERENCE_USER_ID, source.headers["X-Reference-User"])
            val session = requireNotNull(source.session)
            activeSession = session
            val playSessionId = requireNotNull(session.playSessionId)
            assertEquals("open", referenceSessionState(currentServerUrl, playSessionId))

            assertTrue(
                providerRepository.closePlayback(
                    session = session,
                    reason = ProviderPlaybackCloseReason.STOPPED,
                )
            )
            activeSession = null
            assertEquals("closed", referenceSessionState(currentServerUrl, playSessionId))

            withContext(Dispatchers.Main.immediate) {
                playerManager.play(
                    command = MediaCommand.Common(news.id),
                    applyContinueWatching = false,
                )
            }
            val (playbackState, playbackFailure) = withTimeout(PLAYER_READY_TIMEOUT_MILLIS) {
                combine(
                    playerManager.playbackState,
                    playerManager.playbackException,
                ) { state, failure -> state to failure }
                    .first { (state, failure) ->
                        state == Player.STATE_READY || failure != null
                    }
            }
            assertNull("Media3 failed before reaching READY", playbackFailure)
            assertEquals(Player.STATE_READY, playbackState)
            assertEquals(
                "open",
                referenceSessionState(currentServerUrl, REFERENCE_NEWS_PLAY_SESSION_ID),
            )
            withContext(Dispatchers.Main.immediate) {
                playerManager.release()
            }
            awaitReferenceSessionState(
                serverUrl = currentServerUrl,
                playSessionId = REFERENCE_NEWS_PLAY_SESSION_ID,
                expectedState = "closed",
            )

            val pluginAfterPlayback = pluginRepository.installedPlugins().single { installed ->
                installed.packageName == REFERENCE_PACKAGE
            }
            assertEquals(ExtensionState.ENABLED, pluginAfterPlayback.state)
        } finally {
            withContext(Dispatchers.Main.immediate) {
                playerManager.release()
            }
            activeSession?.let { session ->
                runCatching {
                    providerRepository.closePlayback(
                        session = session,
                        reason = ProviderPlaybackCloseReason.STOPPED,
                    )
                }
            }
            serverUrl?.let { baseUrl ->
                runCatching {
                    awaitReferenceSessionState(
                        serverUrl = baseUrl,
                        playSessionId = REFERENCE_NEWS_PLAY_SESSION_ID,
                        expectedState = "closed",
                        timeoutMillis = CLEANUP_TIMEOUT_MILLIS,
                    )
                }
            }
            providerPlaylistUrl?.let { playlistUrl ->
                runCatching { playlistRepository.unsubscribe(playlistUrl) }
            }
            if (pluginPackage != null && pluginService != null) {
                runCatching {
                    pluginRepository.revoke(
                        packageName = checkNotNull(pluginPackage),
                        serviceName = checkNotNull(pluginService),
                    )
                }
            }
            context.settings.edit { preferences ->
                preferences[PreferencesKeys.EXTERNAL_EXTENSIONS] = false
            }
        }
    }

    private suspend fun awaitReferenceSessionState(
        serverUrl: String,
        playSessionId: String,
        expectedState: String,
        timeoutMillis: Long = SESSION_CLOSE_TIMEOUT_MILLIS,
    ) {
        withTimeout(timeoutMillis) {
            while (true) {
                val state = runCatching {
                    referenceSessionState(serverUrl, playSessionId)
                }.getOrNull()
                if (state == expectedState) return@withTimeout
                delay(SESSION_STATE_POLL_MILLIS)
            }
        }
    }

    private fun referenceSessionState(
        serverUrl: String,
        playSessionId: String,
    ): String {
        val connection = URL(
            "$serverUrl/reference-provider/sessions/$playSessionId"
        ).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 2_000
            connection.readTimeout = 2_000
            connection.setRequestProperty("X-Emby-Token", REFERENCE_ACCESS_TOKEN)
            assertEquals(200, connection.responseCode)
            val payload = connection.inputStream.bufferedReader().use { reader ->
                Json.parseToJsonElement(reader.readText()).jsonObject
            }
            payload.getValue("state").jsonPrimitive.content
        } finally {
            connection.disconnect()
        }
    }

    private fun assertMockServerReady(serverUrl: String) {
        val healthUrl = "$serverUrl/health"
        val connection = URL(healthUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = MOCK_SERVER_HEALTH_TIMEOUT_MILLIS
            connection.readTimeout = MOCK_SERVER_HEALTH_TIMEOUT_MILLIS
            assertEquals(
                "Mock server is not ready at $healthUrl",
                200,
                connection.responseCode,
            )
            assertEquals(
                "Mock server returned an unexpected health response at $healthUrl",
                "ok",
                connection.inputStream.bufferedReader().use { reader -> reader.readText() },
            )
        } catch (failure: Exception) {
            throw AssertionError("Mock server is unavailable at $healthUrl", failure)
        } finally {
            connection.disconnect()
        }
    }

    private fun assertWorkManagerNetworkReady(context: Context) {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivityManager.getNetworkCapabilities(
            connectivityManager.activeNetwork
        )
        assertTrue(
            "Device network must be validated before testing the CONNECTED WorkManager constraint",
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
        )
    }

    private companion object {
        const val REFERENCE_PACKAGE = "com.m3u.testing.extension.reference"
        const val PLAYLIST_TITLE = "Reference Provider E2E"
        const val DEFAULT_SERVER_URL = "http://10.0.2.2:8080"
        const val REFERENCE_ACCESS_TOKEN = "mock-reference-access-token"
        const val REFERENCE_USER_ID = "reference-user-id"
        const val REFERENCE_NEWS_ID = "reference.news"
        const val REFERENCE_NEWS_PLAY_SESSION_ID =
            "reference-play-session-reference.news"
        const val OUTPUT_CHANNEL_COUNT_KEY = "channel-count"
        const val WORK_TIMEOUT_MILLIS = 60_000L
        const val PLAYER_READY_TIMEOUT_MILLIS = 30_000L
        const val SESSION_CLOSE_TIMEOUT_MILLIS = 15_000L
        const val CLEANUP_TIMEOUT_MILLIS = 5_000L
        const val SESSION_STATE_POLL_MILLIS = 100L
        const val MOCK_SERVER_HEALTH_TIMEOUT_MILLIS = 5_000
        val REFERENCE_EXTENSION_ID = ExtensionId("com.m3u.reference.provider")
        val REFERENCE_PROVIDER_KIND = ProviderKind("reference")
    }
}
