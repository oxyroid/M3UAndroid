package com.m3u.testing

import androidx.datastore.preferences.core.edit
import androidx.test.platform.app.InstrumentationRegistry
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.settings
import com.m3u.data.database.model.DataSource
import com.m3u.data.repository.plugin.PluginEnableResult
import com.m3u.data.repository.provider.ProviderOperationException
import com.m3u.data.repository.provider.ProviderPlaybackCloseReason
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
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalProviderEndToEndTest {
    @Test
    fun referenceProviderCompletesSubscriptionPlaybackAndSessionCloseAcrossBinder() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext.applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            DebugExtensionPlatformEntryPoint::class.java,
        )
        val pluginRepository = entryPoint.pluginRepository()
        val providerRepository = entryPoint.providerRepository()
        val playlistRepository = entryPoint.playlistRepository()
        var playlistUrl: String? = null
        var pluginPackage: String? = null
        var pluginService: String? = null

        try {
            context.settings.edit { preferences ->
                preferences[PreferencesKeys.EXTERNAL_EXTENSIONS] = true
            }
            val plugin = pluginRepository.installedPlugins().single { installed ->
                installed.packageName == REFERENCE_PACKAGE
            }
            pluginPackage = plugin.packageName
            pluginService = plugin.serviceName
            val enableResult = pluginRepository.enable(plugin.packageName, plugin.serviceName)
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
                assertEquals("provider.authentication_failed", (failure as ProviderOperationException).code)
            }
            val pluginAfterRejectedLogins = pluginRepository.installedPlugins().single { installed ->
                installed.packageName == REFERENCE_PACKAGE
            }
            assertEquals(ExtensionState.ENABLED, pluginAfterRejectedLogins.state)

            val subscription = try {
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
                                providerRepository.stageCredential("reference-password"),
                        ),
                    )
                )
            } catch (error: ProviderOperationException) {
                throw AssertionError(
                    "Provider subscription failed: ${error.code} ${error.details}",
                    error,
                )
            }
            playlistUrl = subscription.playlistUrl
            assertEquals(2, subscription.channelCount)

            val playlist = requireNotNull(
                playlistRepository.getPlaylistWithChannels(subscription.playlistUrl)
            )
            assertEquals(DataSource.Provider, playlist.playlist.source)
            assertEquals(
                setOf("reference.news", "reference.sports"),
                playlist.channels.mapTo(mutableSetOf()) { channel -> channel.relationId },
            )

            val news = playlist.channels.single { channel ->
                channel.relationId == "reference.news"
            }
            val playback = requireNotNull(providerRepository.resolvePlayback(news.id))
            val session = requireNotNull(playback.session)
            val manifest = get(playback.url, playback.headers)
            assertEquals(200, manifest.statusCode)
            assertTrue(manifest.body.startsWith("#EXTM3U"))

            val stateUrl = "$serverUrl/reference-provider/sessions/${session.playSessionId}"
            assertEquals("open", get(stateUrl, playback.headers).jsonField("state"))
            assertTrue(
                providerRepository.closePlayback(
                    session = session,
                    reason = ProviderPlaybackCloseReason.STOPPED,
                )
            )
            assertEquals("closed", get(stateUrl, playback.headers).jsonField("state"))
        } finally {
            playlistUrl?.let { value ->
                runCatching { playlistRepository.unsubscribe(value) }
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

    private fun get(url: String, headers: Map<String, String>): HttpResult {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            headers.forEach(connection::setRequestProperty)
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            HttpResult(statusCode, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    private fun HttpResult.jsonField(name: String): String {
        assertEquals(200, statusCode)
        return Json.parseToJsonElement(body).jsonObject.getValue(name).jsonPrimitive.content
    }

    private data class HttpResult(
        val statusCode: Int,
        val body: String,
    )

    private companion object {
        const val REFERENCE_PACKAGE = "com.m3u.testing.extension.reference"
        const val PLAYLIST_TITLE = "Reference Provider E2E"
        const val DEFAULT_SERVER_URL = "http://10.0.2.2:8080"
        val REFERENCE_EXTENSION_ID = ExtensionId("com.m3u.reference.provider")
        val REFERENCE_PROVIDER_KIND = ProviderKind("reference")
    }
}
