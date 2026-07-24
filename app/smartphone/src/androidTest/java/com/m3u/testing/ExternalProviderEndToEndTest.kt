package com.m3u.testing

import androidx.datastore.preferences.core.edit
import androidx.test.platform.app.InstrumentationRegistry
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.settings
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.DataSource
import com.m3u.data.repository.plugin.PluginEnableResult
import com.m3u.data.repository.provider.ProviderOperationException
import com.m3u.data.repository.provider.ProviderPlaybackCloseReason
import com.m3u.data.repository.provider.ProviderPlaybackSession
import com.m3u.data.repository.provider.ProviderSubscriptionRequest
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionState
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.api.subscription.SubscriptionProviderSettingKeys
import com.m3u.smartphone.DebugExtensionPlatformEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalProviderEndToEndTest {
    @Test
    fun referenceProviderCompletesCredentialBackedPlaybackSessionAcrossBinder() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext.applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            DebugExtensionPlatformEntryPoint::class.java,
        )
        val pluginRepository = entryPoint.pluginRepository()
        val providerRepository = entryPoint.providerRepository()
        val playlistRepository = entryPoint.playlistRepository()
        var pluginPackage: String? = null
        var pluginService: String? = null
        var providerPlaylistUrl: String? = null
        var activeSession: ProviderPlaybackSession? = null

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
            val serverUrl = InstrumentationRegistry.getArguments()
                .getString("m3uMockServerUrl", DEFAULT_SERVER_URL)
                .trimEnd('/')
            repeat(3) {
                val failure = runCatching {
                    providerRepository.subscribe(
                        ProviderSubscriptionRequest(
                            title = PLAYLIST_TITLE,
                            providerId = REFERENCE_EXTENSION_ID,
                            providerKind = REFERENCE_PROVIDER_KIND,
                            settingValues = mapOf(
                                SubscriptionProviderSettingKeys.BaseUrl to serverUrl,
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
                        SubscriptionProviderSettingKeys.BaseUrl to serverUrl,
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

            val refresh = providerRepository.refresh(subscription.playlistUrl)
            assertEquals(2, refresh.channelCount)
            val channels = requireNotNull(
                playlistRepository.getPlaylistWithChannels(subscription.playlistUrl)
            ).channels
            assertEquals(2, channels.size)
            val news = channels.single { channel -> channel.relationId == REFERENCE_NEWS_ID }
            assertEquals(Channel.URL_DYNAMIC, news.url)

            val source = requireNotNull(providerRepository.resolvePlayback(news.id))
            assertNotEquals(Channel.URL_DYNAMIC, source.url)
            assertEquals(
                "$serverUrl/reference-provider/stream/$REFERENCE_NEWS_ID/index.m3u8",
                source.url,
            )
            assertEquals(REFERENCE_ACCESS_TOKEN, source.headers["X-Emby-Token"])
            assertEquals(REFERENCE_USER_ID, source.headers["X-Reference-User"])
            val session = requireNotNull(source.session)
            activeSession = session
            val playSessionId = requireNotNull(session.playSessionId)
            assertEquals("open", referenceSessionState(serverUrl, playSessionId))

            assertTrue(
                providerRepository.closePlayback(
                    session = session,
                    reason = ProviderPlaybackCloseReason.STOPPED,
                )
            )
            activeSession = null
            assertEquals("closed", referenceSessionState(serverUrl, playSessionId))

            val pluginAfterPlayback = pluginRepository.installedPlugins().single { installed ->
                installed.packageName == REFERENCE_PACKAGE
            }
            assertEquals(ExtensionState.ENABLED, pluginAfterPlayback.state)
        } finally {
            activeSession?.let { session ->
                runCatching {
                    providerRepository.closePlayback(
                        session = session,
                        reason = ProviderPlaybackCloseReason.STOPPED,
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

    private companion object {
        const val REFERENCE_PACKAGE = "com.m3u.testing.extension.reference"
        const val PLAYLIST_TITLE = "Reference Provider E2E"
        const val DEFAULT_SERVER_URL = "http://10.0.2.2:8080"
        const val REFERENCE_ACCESS_TOKEN = "mock-reference-access-token"
        const val REFERENCE_USER_ID = "reference-user-id"
        const val REFERENCE_NEWS_ID = "reference.news"
        val REFERENCE_EXTENSION_ID = ExtensionId("com.m3u.reference.provider")
        val REFERENCE_PROVIDER_KIND = ProviderKind("reference")
    }
}
