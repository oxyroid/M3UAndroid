package com.m3u.data.extension.emby

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m3u.data.database.M3UDatabase
import com.m3u.data.extension.security.AndroidKeystoreCredentialVault
import com.m3u.data.extension.security.CredentialResolver
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.HookResult
import com.m3u.extension.api.subscription.EmbyCompatibleProviderKinds
import com.m3u.extension.api.subscription.PlaybackMethod
import com.m3u.extension.api.subscription.PlaybackMethods
import com.m3u.extension.api.subscription.PlaybackPreferences
import com.m3u.extension.api.subscription.PlaybackReference
import com.m3u.extension.api.subscription.PlaybackSessionDescriptor
import com.m3u.extension.api.subscription.PlaybackSessionEvent
import com.m3u.extension.api.subscription.PlaybackSessionEvents
import com.m3u.extension.api.subscription.PlaybackSessionUpdateRequest
import com.m3u.extension.api.subscription.PlaybackSessionUpdateResult
import com.m3u.extension.api.subscription.ProviderAccountReference
import com.m3u.extension.api.subscription.ProviderCredential
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.api.subscription.ProviderMediaKinds
import com.m3u.extension.api.subscription.SubscriptionContentBrowseRequest
import com.m3u.extension.api.subscription.SubscriptionContentBrowseResult
import com.m3u.extension.api.subscription.SubscriptionContentItemDescriptor
import com.m3u.extension.api.subscription.SubscriptionContentRefreshRequest
import com.m3u.extension.api.subscription.SubscriptionContentRefreshResult
import com.m3u.extension.api.subscription.SubscriptionHookSpecs
import com.m3u.extension.api.subscription.SubscriptionRefreshReason
import com.m3u.extension.api.subscription.ValidatedProviderAccount
import com.m3u.extension.runtime.ExtensionRegistrationResult
import com.m3u.extension.runtime.ExtensionRuntime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmbyCompatibleProviderHookTest {
    @Test
    fun liveTvPermissionDenialStillAllowsVodBrowse() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, M3UDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val credentialVault = AndroidKeystoreCredentialVault(
                context = context,
                keyAlias = "m3u.provider-hook.${UUID.randomUUID()}",
            )
            val client = RecordingClient(
                refreshFailure = EmbyHttpException(403, "Live TV access is disabled"),
            )
            val provider = EmbyCompatibleProvider(
                applicationContext = context,
                client = client,
                credentialResolver = CredentialResolver(
                    providerDao = database.providerDao(),
                    credentialVault = credentialVault,
                ),
            )
            val runtime = ExtensionRuntime(hostApiVersion = ExtensionApiVersions.Current)
            assertTrue(runtime.register(provider) is ExtensionRegistrationResult.Registered)

            val refresh = runtime.invoke(
                extensionId = EmbyCompatibleProvider.ID,
                spec = SubscriptionHookSpecs.Refresh,
                request = SubscriptionContentRefreshRequest(
                    account = ACCOUNT,
                    credential = ProviderCredential(credentialVault.stage(ACCESS_TOKEN)),
                    reason = SubscriptionRefreshReason.Initial,
                ),
            ).outcome
            assertTrue(refresh is HookResult.Success)
            assertTrue(
                (refresh as HookResult.Success<SubscriptionContentRefreshResult>)
                    .payload
                    .channels
                    .isEmpty()
            )

            val browse = runtime.invoke(
                extensionId = EmbyCompatibleProvider.ID,
                spec = SubscriptionHookSpecs.Browse,
                request = SubscriptionContentBrowseRequest(
                    account = ACCOUNT,
                    credential = ProviderCredential(credentialVault.stage(ACCESS_TOKEN)),
                ),
            ).outcome
            assertTrue(browse is HookResult.Success)
            assertEquals(
                "movie",
                (browse as HookResult.Success<SubscriptionContentBrowseResult>)
                    .payload
                    .items
                    .single()
                    .reference
                    .sourceType,
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun manifestAndTypedHandlersExposeBrowseAndPlaybackUpdate() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, M3UDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val credentialVault = AndroidKeystoreCredentialVault(
                context = context,
                keyAlias = "m3u.provider-hook.${UUID.randomUUID()}",
            )
            val browseCredentialHandle = credentialVault.stage(ACCESS_TOKEN)
            val updateCredentialHandle = credentialVault.stage(ACCESS_TOKEN)
            val client = RecordingClient()
            val provider = EmbyCompatibleProvider(
                applicationContext = context,
                client = client,
                credentialResolver = CredentialResolver(
                    providerDao = database.providerDao(),
                    credentialVault = credentialVault,
                ),
            )
            val runtime = ExtensionRuntime(hostApiVersion = ExtensionApiVersions.Current)

            assertTrue(runtime.register(provider) is ExtensionRegistrationResult.Registered)
            assertTrue(
                provider.manifest.hooks.any { declaration ->
                    declaration.hook == SubscriptionHookSpecs.Browse.hook &&
                        declaration.schemaVersion == SubscriptionHookSpecs.Browse.schemaVersion
                }
            )
            assertTrue(
                provider.manifest.hooks.any { declaration ->
                    declaration.hook == SubscriptionHookSpecs.UpdatePlayback.hook &&
                        declaration.schemaVersion ==
                        SubscriptionHookSpecs.UpdatePlayback.schemaVersion
                }
            )
            assertEquals(
                setOf(
                    SubscriptionHookSpecs.Browse.hook,
                    SubscriptionHookSpecs.UpdatePlayback.hook,
                ),
                provider.handlers
                    .map { handler -> handler.spec.hook }
                    .filterTo(mutableSetOf()) { hook ->
                        hook == SubscriptionHookSpecs.Browse.hook ||
                            hook == SubscriptionHookSpecs.UpdatePlayback.hook
                    },
            )

            val browse = runtime.invoke(
                extensionId = EmbyCompatibleProvider.ID,
                spec = SubscriptionHookSpecs.Browse,
                request = SubscriptionContentBrowseRequest(
                    account = ACCOUNT,
                    credential = ProviderCredential(browseCredentialHandle),
                    cursor = "4",
                    limit = 2,
                ),
            ).outcome
            assertTrue(browse is HookResult.Success)
            val browsePayload =
                (browse as HookResult.Success<SubscriptionContentBrowseResult>).payload
            assertEquals("6", browsePayload.nextCursor)
            assertEquals("movie", browsePayload.items.single().reference.sourceType)
            assertEquals(ACCESS_TOKEN, client.browseAccessToken)
            assertEquals("4", client.browseCursor)
            assertEquals(2, client.browseLimit)

            val update = runtime.invoke(
                extensionId = EmbyCompatibleProvider.ID,
                spec = SubscriptionHookSpecs.UpdatePlayback,
                request = PlaybackSessionUpdateRequest(
                    account = ACCOUNT,
                    credential = ProviderCredential(updateCredentialHandle),
                    reference = MOVIE_REFERENCE.copy(mediaSourceId = "source"),
                    session = PlaybackSessionDescriptor(playSessionId = "session"),
                    event = PlaybackSessionEvents.Paused,
                    positionTicks = 42_000_000L,
                    playMethod = PlaybackMethods.DirectPlay,
                    isPaused = true,
                ),
            ).outcome
            assertTrue(update is HookResult.Success)
            assertTrue(
                (update as HookResult.Success<PlaybackSessionUpdateResult>).payload.accepted
            )
            assertEquals(ACCESS_TOKEN, client.updateAccessToken)
            assertEquals("source", client.updateMediaSourceId)
            assertEquals(PlaybackSessionEvents.Paused, client.updateEvent)
            assertEquals(42_000_000L, client.updatePositionTicks)
            assertEquals(PlaybackMethods.DirectPlay, client.updatePlayMethod)
            assertTrue(client.updateIsPaused)
        } finally {
            database.close()
        }
    }

    private class RecordingClient(
        private val refreshFailure: EmbyHttpException? = null,
    ) : EmbyCompatibleClient {
        var browseAccessToken: String? = null
        var browseCursor: String? = null
        var browseLimit: Int? = null
        var updateAccessToken: String? = null
        var updateMediaSourceId: String? = null
        var updateEvent: PlaybackSessionEvent? = null
        var updatePositionTicks: Long? = null
        var updatePlayMethod: PlaybackMethod? = null
        var updateIsPaused: Boolean = false

        override suspend fun validate(
            baseUrl: String,
            requestedKind: ProviderKind,
            username: String,
            password: String,
        ): EmbyValidation = error("Not used")

        override suspend fun refreshChannels(
            account: ValidatedProviderAccount,
            accessToken: String,
        ): EmbyChannelRefresh {
            refreshFailure?.let { throw it }
            return EmbyChannelRefresh(
                channels = emptyList(),
                totalRecordCount = 0,
            )
        }

        override suspend fun browseContent(
            account: ValidatedProviderAccount,
            accessToken: String,
            parentReference: PlaybackReference?,
            cursor: String?,
            limit: Int,
        ): EmbyContentPage {
            browseAccessToken = accessToken
            browseCursor = cursor
            browseLimit = limit
            return EmbyContentPage(
                items = listOf(
                    SubscriptionContentItemDescriptor(
                        reference = MOVIE_REFERENCE,
                        mediaKind = ProviderMediaKinds.Movie,
                        title = "Movie",
                        playable = true,
                        browsable = false,
                    )
                ),
                nextCursor = "6",
                total = 7,
            )
        }

        override suspend fun resolvePlayback(
            account: ValidatedProviderAccount,
            accessToken: String,
            reference: PlaybackReference,
            preferences: PlaybackPreferences,
        ): EmbyPlaybackSource = error("Not used")

        override suspend fun updatePlayback(
            account: ValidatedProviderAccount,
            accessToken: String,
            reference: PlaybackReference,
            mediaSourceId: String?,
            session: EmbyPlaybackSession,
            event: PlaybackSessionEvent,
            positionTicks: Long,
            playMethod: PlaybackMethod,
            isPaused: Boolean,
        ): Boolean {
            updateAccessToken = accessToken
            updateMediaSourceId = mediaSourceId
            updateEvent = event
            updatePositionTicks = positionTicks
            updatePlayMethod = playMethod
            updateIsPaused = isPaused
            return true
        }

        override suspend fun closePlayback(
            account: ValidatedProviderAccount,
            accessToken: String,
            itemId: String,
            mediaSourceId: String?,
            session: EmbyPlaybackSession,
            positionTicks: Long,
        ): Boolean = error("Not used")
    }

    private companion object {
        const val ACCESS_TOKEN = "provider-access-token"
        val ACCOUNT = ProviderAccountReference(
            accountId = "account",
            providerId = EmbyCompatibleProvider.ID,
            providerKind = EmbyCompatibleProviderKinds.Emby,
            baseUrl = "https://provider.example",
            serverId = "server",
            serverName = "Provider",
            serverVersion = "1",
            userId = "user",
            username = "user",
        )
        val MOVIE_REFERENCE = PlaybackReference(
            providerId = EmbyCompatibleProvider.ID,
            itemId = "movie",
            sourceType = "movie",
        )
    }
}
