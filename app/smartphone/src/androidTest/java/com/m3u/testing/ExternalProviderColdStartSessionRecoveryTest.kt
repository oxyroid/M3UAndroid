package com.m3u.testing

import android.content.Context
import android.os.Process
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.settings
import com.m3u.data.database.dao.ProviderDao
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.DataSource
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.plugin.ExtensionPluginRepository
import com.m3u.data.repository.plugin.PluginEnableResult
import com.m3u.data.repository.provider.ProviderPlaybackSession
import com.m3u.data.repository.provider.ProviderSubscriptionRequest
import com.m3u.data.repository.provider.SubscriptionProviderRepository
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.api.subscription.SubscriptionProviderSettingKeys
import com.m3u.smartphone.DebugExtensionPlatformEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ExternalProviderColdStartSessionRecoveryTest {
    @Test
    fun phase1_opensAndPersistsExternalProviderSession() = runBlocking {
        val fixture = fixture()
        val initialState = fixture.captureInitialState()
        fixture.markerFile.parentFile?.mkdirs()
        fixture.markerFile.writeText(
            JSONObject()
                .put(MARKER_HOST_PID, Process.myPid())
                .putInitialState(initialState)
                .toString()
        )
        try {
            fixture.context.settings.edit { preferences ->
                preferences[PreferencesKeys.EXTERNAL_EXTENSIONS] = true
            }
            val plugin = fixture.pluginRepository.installedPlugins().single { installed ->
                installed.packageName == REFERENCE_PACKAGE
            }
            if (initialState.pluginEnabled) {
                assertTrue(
                    "The previously enabled reference provider could not be restored",
                    fixture.pluginRepository.restoreEnabled() > 0,
                )
            } else {
                val enableResult = fixture.pluginRepository.enable(
                    plugin.packageName,
                    plugin.serviceName,
                    checkNotNull(plugin.authorizationToken),
                )
                assertTrue(
                    "Reference provider could not be enabled: $enableResult",
                    enableResult is PluginEnableResult.Enabled,
                )
            }
            fixture.removeTestSubscriptions()

            val serverUrl = InstrumentationRegistry.getArguments()
                .getString("m3uMockServerUrl", DEFAULT_SERVER_URL)
                .trimEnd('/')
            assertMockServerReady(serverUrl)
            val descriptor = fixture.providerRepository.discoverProviders().single { provider ->
                provider.descriptor.providerId == REFERENCE_EXTENSION_ID
            }.descriptor
            assertTrue(
                descriptor.variants.any { variant -> variant.kind == REFERENCE_PROVIDER_KIND }
            )
            val subscription = fixture.providerRepository.subscribe(
                ProviderSubscriptionRequest(
                    title = PLAYLIST_TITLE,
                    providerId = REFERENCE_EXTENSION_ID,
                    providerKind = REFERENCE_PROVIDER_KIND,
                    settingValues = mapOf(
                        SubscriptionProviderSettingKeys.BaseUrl to serverUrl,
                        SubscriptionProviderSettingKeys.Username to REFERENCE_USERNAME,
                    ),
                    credentialHandles = mapOf(
                        SubscriptionProviderSettingKeys.Password to
                            fixture.providerRepository.stageCredential(REFERENCE_PASSWORD),
                    ),
                )
            )
            val channels = requireNotNull(
                fixture.playlistRepository.getPlaylistWithChannels(subscription.playlistUrl)
            ).channels
            val news = channels.single { channel -> channel.relationId == REFERENCE_NEWS_ID }
            assertEquals(Channel.URL_DYNAMIC, news.url)

            val source = requireNotNull(fixture.providerRepository.resolvePlayback(news.id))
            val session = requireNotNull(source.session)
            val playSessionId = requireNotNull(session.playSessionId)
            assertEquals(REFERENCE_PLAY_SESSION_ID, playSessionId)
            assertPersistedSession(fixture.providerDao, session)
            assertEquals(1, fixture.providerDao.countPlaybackSessions(session.accountId))
            assertEquals(
                ReferenceSessionState(
                    state = "open",
                    closeCount = 0,
                    lastCloseReason = null,
                ),
                referenceSessionState(serverUrl, playSessionId),
            )

            fixture.markerFile.writeText(
                JSONObject()
                    .put(MARKER_HOST_PID, Process.myPid())
                    .putInitialState(initialState)
                    .put(MARKER_SESSION_ID, session.id)
                    .put(MARKER_ACCOUNT_ID, session.accountId)
                    .put(MARKER_PLAY_SESSION_ID, playSessionId)
                    .put(MARKER_PLAYLIST_URL, subscription.playlistUrl)
                    .put(MARKER_SERVER_URL, serverUrl)
                    .toString()
            )
        } catch (failure: Throwable) {
            runCatching { fixture.cleanup(initialState) }
            throw failure
        }
    }

    @Test
    fun phase2_coldStartClosesSessionOnceAndDeletesTombstone() = runBlocking {
        val fixture = fixture()
        var initialState = fixture.captureInitialState()
        try {
            assertTrue(
                "Phase 1 marker is missing; run this class with Android Test Orchestrator",
                fixture.markerFile.isFile,
            )
            val marker = JSONObject(fixture.markerFile.readText())
            initialState = marker.initialState()
            val phase1Pid = marker.getInt(MARKER_HOST_PID)
            val sessionId = marker.getString(MARKER_SESSION_ID)
            val accountId = marker.getString(MARKER_ACCOUNT_ID)
            val playSessionId = marker.getString(MARKER_PLAY_SESSION_ID)
            val playlistUrl = marker.getString(MARKER_PLAYLIST_URL)
            val serverUrl = marker.getString(MARKER_SERVER_URL)
            assertNotEquals(
                "Both phases ran in the same host process; the cold-start path was not exercised",
                phase1Pid,
                Process.myPid(),
            )
            assertMockServerReady(serverUrl)

            val recoveredState = withTimeout(RECOVERY_TIMEOUT_MILLIS) {
                while (true) {
                    val remoteState = runCatching {
                        referenceSessionState(serverUrl, playSessionId)
                    }.getOrNull()
                    if (remoteState != null && remoteState.closeCount > 1) {
                        error("Cold-start recovery closed the remote session more than once")
                    }
                    if (
                        fixture.providerDao.getPlaybackSession(sessionId) == null &&
                        remoteState != null &&
                        remoteState.state == "closed" &&
                        remoteState.closeCount == 1
                    ) {
                        return@withTimeout remoteState
                    }
                    delay(SESSION_STATE_POLL_MILLIS)
                }
                error("Unreachable")
            }
            assertEquals("recovery", recoveredState.lastCloseReason)
            assertNull(fixture.providerDao.getPlaybackSession(sessionId))
            assertEquals(
                accountId,
                fixture.providerDao.getAccountByPlaylistUrl(playlistUrl)?.id,
            )
            assertEquals(0, fixture.providerDao.countPlaybackSessions(accountId))

            fixture.providerRepository.closeOrphanedPlaybackSessions()

            assertNull(fixture.providerDao.getPlaybackSession(sessionId))
            assertEquals(0, fixture.providerDao.countPlaybackSessions(accountId))
            assertEquals(
                ReferenceSessionState(
                    state = "closed",
                    closeCount = 1,
                    lastCloseReason = "recovery",
                ),
                referenceSessionState(serverUrl, playSessionId),
            )
        } finally {
            fixture.cleanup(initialState)
        }
    }

    private fun fixture(): TestFixture {
        val context = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            DebugExtensionPlatformEntryPoint::class.java,
        )
        return TestFixture(
            context = context,
            providerDao = entryPoint.providerDao(),
            pluginRepository = entryPoint.pluginRepository(),
            providerRepository = entryPoint.providerRepository(),
            playlistRepository = entryPoint.playlistRepository(),
        )
    }

    private suspend fun assertPersistedSession(
        providerDao: ProviderDao,
        session: ProviderPlaybackSession,
    ) {
        val persisted = providerDao.getPlaybackSession(session.id)
        assertNotNull("Playback tombstone was not persisted before returning the source", persisted)
        assertEquals(session.accountId, persisted?.accountId)
        assertEquals(session.providerId, persisted?.providerId)
        assertEquals(session.itemId, persisted?.itemId)
        assertEquals(session.playSessionId, persisted?.playSessionId)
        assertEquals(session.liveStreamId, persisted?.liveStreamId)
    }

    private suspend fun TestFixture.removeTestSubscriptions() {
        playlistRepository.getBySource(DataSource.Provider)
            .filter { playlist -> playlist.title == PLAYLIST_TITLE }
            .forEach { playlist -> playlistRepository.unsubscribe(playlist.url) }
    }

    private suspend fun TestFixture.captureInitialState(): InitialState {
        val plugin = pluginRepository.installedPlugins().single { installed ->
            installed.packageName == REFERENCE_PACKAGE
        }
        return InitialState(
            externalExtensionsEnabled =
                context.settings.data.first()[PreferencesKeys.EXTERNAL_EXTENSIONS] ?: false,
            pluginTrusted = plugin.trusted,
            pluginEnabled = plugin.enabled,
            pluginState = plugin.state.name,
        )
    }

    private suspend fun TestFixture.cleanup(initialState: InitialState) {
        runCatching { providerRepository.closeOrphanedPlaybackSessions() }
        runCatching { removeTestSubscriptions() }
        runCatching {
            val plugin = pluginRepository.installedPlugins()
                .firstOrNull { installed -> installed.packageName == REFERENCE_PACKAGE }
                ?: return@runCatching
            when {
                !initialState.pluginTrusted -> {
                    pluginRepository.revoke(plugin.packageName, plugin.serviceName)
                }

                !initialState.pluginEnabled -> {
                    pluginRepository.disable(
                        plugin.extensionId ?: REFERENCE_EXTENSION_ID.value
                    )
                }

                !plugin.enabled -> {
                    pluginRepository.enable(
                        plugin.packageName,
                        plugin.serviceName,
                        checkNotNull(plugin.authorizationToken),
                    )
                }

                else -> Unit
            }
        }
        context.settings.edit { preferences ->
            preferences[PreferencesKeys.EXTERNAL_EXTENSIONS] =
                initialState.externalExtensionsEnabled
        }
        markerFile.delete()
    }

    private fun JSONObject.putInitialState(initialState: InitialState): JSONObject =
        put(
            MARKER_INITIAL_EXTERNAL_EXTENSIONS,
            initialState.externalExtensionsEnabled,
        )
            .put(MARKER_INITIAL_PLUGIN_TRUSTED, initialState.pluginTrusted)
            .put(MARKER_INITIAL_PLUGIN_ENABLED, initialState.pluginEnabled)
            .put(MARKER_INITIAL_PLUGIN_STATE, initialState.pluginState)

    private fun JSONObject.initialState(): InitialState = InitialState(
        externalExtensionsEnabled = getBoolean(MARKER_INITIAL_EXTERNAL_EXTENSIONS),
        pluginTrusted = getBoolean(MARKER_INITIAL_PLUGIN_TRUSTED),
        pluginEnabled = getBoolean(MARKER_INITIAL_PLUGIN_ENABLED),
        pluginState = getString(MARKER_INITIAL_PLUGIN_STATE),
    )

    private fun referenceSessionState(
        serverUrl: String,
        playSessionId: String,
    ): ReferenceSessionState {
        val connection = URL(
            "$serverUrl/reference-provider/sessions/$playSessionId"
        ).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = NETWORK_TIMEOUT_MILLIS
            connection.readTimeout = NETWORK_TIMEOUT_MILLIS
            connection.setRequestProperty("X-Emby-Token", REFERENCE_ACCESS_TOKEN)
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Reference session endpoint returned ${connection.responseCode}"
            }
            val payload = connection.inputStream.bufferedReader().use { reader ->
                Json.parseToJsonElement(reader.readText()).jsonObject
            }
            ReferenceSessionState(
                state = payload.getValue("state").jsonPrimitive.content,
                closeCount = payload.getValue("close_count").jsonPrimitive.content.toInt(),
                lastCloseReason = payload["last_close_reason"]?.jsonPrimitive?.content,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun assertMockServerReady(serverUrl: String) {
        val connection = URL("$serverUrl/health").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = NETWORK_TIMEOUT_MILLIS
            connection.readTimeout = NETWORK_TIMEOUT_MILLIS
            assertEquals(
                "Mock server is not ready at $serverUrl",
                HttpURLConnection.HTTP_OK,
                connection.responseCode,
            )
        } finally {
            connection.disconnect()
        }
    }

    private data class TestFixture(
        val context: Context,
        val providerDao: ProviderDao,
        val pluginRepository: ExtensionPluginRepository,
        val providerRepository: SubscriptionProviderRepository,
        val playlistRepository: PlaylistRepository,
    ) {
        val markerFile
            get() = context.noBackupFilesDir.resolve(MARKER_RELATIVE_PATH)
    }

    private data class ReferenceSessionState(
        val state: String,
        val closeCount: Int,
        val lastCloseReason: String?,
    )

    private data class InitialState(
        val externalExtensionsEnabled: Boolean,
        val pluginTrusted: Boolean,
        val pluginEnabled: Boolean,
        val pluginState: String,
    )

    private companion object {
        const val REFERENCE_PACKAGE = "com.m3u.testing.extension.reference"
        const val PLAYLIST_TITLE = "Reference Provider Cold Start Recovery"
        const val DEFAULT_SERVER_URL = "http://10.0.2.2:8080"
        const val REFERENCE_USERNAME = "m3u"
        const val REFERENCE_PASSWORD = "reference-password"
        const val REFERENCE_ACCESS_TOKEN = "mock-reference-access-token"
        const val REFERENCE_NEWS_ID = "reference.news"
        const val REFERENCE_PLAY_SESSION_ID = "reference-play-session-reference.news"
        const val MARKER_RELATIVE_PATH =
            "external-provider-cold-start/session-recovery-v1.json"
        const val MARKER_HOST_PID = "hostPid"
        const val MARKER_INITIAL_EXTERNAL_EXTENSIONS = "initialExternalExtensions"
        const val MARKER_INITIAL_PLUGIN_TRUSTED = "initialPluginTrusted"
        const val MARKER_INITIAL_PLUGIN_ENABLED = "initialPluginEnabled"
        const val MARKER_INITIAL_PLUGIN_STATE = "initialPluginState"
        const val MARKER_SESSION_ID = "sessionId"
        const val MARKER_ACCOUNT_ID = "accountId"
        const val MARKER_PLAY_SESSION_ID = "playSessionId"
        const val MARKER_PLAYLIST_URL = "playlistUrl"
        const val MARKER_SERVER_URL = "serverUrl"
        const val RECOVERY_TIMEOUT_MILLIS = 60_000L
        const val SESSION_STATE_POLL_MILLIS = 100L
        const val NETWORK_TIMEOUT_MILLIS = 2_000
        val REFERENCE_EXTENSION_ID = ExtensionId("com.m3u.reference.provider")
        val REFERENCE_PROVIDER_KIND = ProviderKind("reference")
    }
}
