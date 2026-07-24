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
import com.m3u.extension.api.subscription.PlaybackPreferences
import com.m3u.extension.api.subscription.PlaybackReference
import com.m3u.extension.api.subscription.PlaybackSourceResolveRequest
import com.m3u.extension.api.subscription.PlaybackSourceResolveResult
import com.m3u.extension.api.subscription.ProviderAccountReference
import com.m3u.extension.api.subscription.ProviderCredential
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.api.subscription.SubscriptionHookSpecs
import com.m3u.extension.api.subscription.ValidatedProviderAccount
import com.m3u.extension.runtime.ExtensionRegistrationResult
import com.m3u.extension.runtime.ExtensionRuntime
import com.m3u.extension.runtime.InvocationPolicy
import java.util.UUID
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmbyCompatibleProviderPlaybackFailureTest {
    @Test
    fun cleanupAdmissionIsBoundedAndReusable() {
        val scheduler = EmbyPlaybackCleanupScheduler()
        val reservations = List(EmbyPlaybackCleanupScheduler.MAX_PENDING_CLEANUPS) {
            requireNotNull(scheduler.tryReserve())
        }

        assertNull(scheduler.tryReserve())
        reservations.first().release()
        val replacement = scheduler.tryReserve()
        assertNotNull(replacement)

        replacement?.release()
        reservations.drop(1).forEach(EmbyPlaybackCleanupAdmission::release)
    }

    @Test
    fun oversizedSessionIdentifierIsClosedBeforeRuntimeBoundary() = runBlocking {
        val oversizedSessionId = "s".repeat(513)
        val client = PlaybackClient(
            source = source(
                session = EmbyPlaybackSession(
                    playSessionId = oversizedSessionId,
                    liveStreamId = "live-stream",
                )
            )
        )

        val outcome = invokePlayback(client)
        client.awaitClose()

        assertTrue(outcome is HookResult.Failure)
        assertEquals(1, client.closeRequests.size)
        assertEquals(oversizedSessionId, client.closeRequests.single().session.playSessionId)
        assertEquals("live-stream", client.closeRequests.single().session.liveStreamId)
    }

    @Test
    fun responseAboveRuntimeLimitIsClosedBeforeRuntimeBoundary() = runBlocking {
        val policy = InvocationPolicy(maxPayloadBytes = 1_024)
        val client = PlaybackClient(
            source = source(
                headers = mapOf("X-Large" to "x".repeat(2_048)),
                session = EmbyPlaybackSession(
                    playSessionId = "play-session",
                    liveStreamId = "live-stream",
                ),
            )
        )

        val outcome = invokePlayback(client, policy)
        client.awaitClose()

        assertTrue(outcome is HookResult.Failure)
        assertEquals(1, client.closeRequests.size)
        assertEquals("media-source", client.closeRequests.single().mediaSourceId)
    }

    @Test
    fun validRuntimeResultLeavesSessionForRepositoryOwnership() = runBlocking {
        val client = PlaybackClient(
            source = source(
                session = EmbyPlaybackSession(
                    playSessionId = "play-session",
                    liveStreamId = "live-stream",
                )
            )
        )

        val outcome = invokePlayback(client)

        assertTrue(outcome is HookResult.Success)
        val payload = (outcome as HookResult.Success<PlaybackSourceResolveResult>).payload
        assertEquals("play-session", payload.session?.playSessionId)
        assertTrue(client.closeRequests.isEmpty())
    }

    @Test
    fun compensationDoesNotHoldRuntimePastItsInvocationDeadline() = runBlocking {
        val closeRelease = CompletableDeferred<Unit>()
        val client = PlaybackClient(
            source = source(
                session = EmbyPlaybackSession(
                    playSessionId = "s".repeat(513),
                    liveStreamId = "live-stream",
                )
            ),
            closeRelease = closeRelease,
        )
        lateinit var outcome: HookResult<PlaybackSourceResolveResult>
        var invocationElapsedMillis = Long.MAX_VALUE

        outcome = withTimeout(2_000) {
            invokePlayback(
                client = client,
                policy = InvocationPolicy(
                    timeoutMillis = 250,
                    maxPayloadBytes = 4_096,
                ),
                onInvocationElapsed = { elapsed ->
                    invocationElapsedMillis = elapsed
                },
            )
        }

        assertTrue(outcome is HookResult.Failure)
        assertTrue(
            "Invocation took ${invocationElapsedMillis}ms",
            invocationElapsedMillis < 500,
        )
        client.awaitClose()
        closeRelease.complete(Unit)
        Unit
    }

    private suspend fun invokePlayback(
        client: PlaybackClient,
        policy: InvocationPolicy = InvocationPolicy(maxPayloadBytes = 4_096),
        onInvocationElapsed: (Long) -> Unit = {},
    ): HookResult<PlaybackSourceResolveResult> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, M3UDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val credentialVault = AndroidKeystoreCredentialVault(
                context = context,
                keyAlias = "m3u.provider-playback-failure.${UUID.randomUUID()}",
            )
            val provider = EmbyCompatibleProvider(
                client = client,
                credentialResolver = CredentialResolver(
                    providerDao = database.providerDao(),
                    credentialVault = credentialVault,
                ),
                invocationPolicy = policy,
            )
            val runtime = ExtensionRuntime(
                hostApiVersion = ExtensionApiVersions.Current,
                invocationPolicy = policy,
            )
            assertTrue(
                runtime.register(provider) is ExtensionRegistrationResult.Registered
            )
            val credentialHandle = credentialVault.stage("access-token")
            lateinit var outcome: HookResult<PlaybackSourceResolveResult>
            val invocationElapsed = measureTimeMillis {
                outcome = runtime.invoke(
                    extensionId = EmbyCompatibleProvider.ID,
                    spec = SubscriptionHookSpecs.ResolvePlayback,
                    request = PlaybackSourceResolveRequest(
                        account = ProviderAccountReference(
                            accountId = "account",
                            providerId = EmbyCompatibleProvider.ID,
                            providerKind = EmbyCompatibleProviderKinds.Emby,
                            baseUrl = "https://provider.example",
                            serverId = "server",
                            serverName = "Provider",
                            serverVersion = "1",
                            userId = "user",
                            username = "user",
                        ),
                        credential = ProviderCredential(credentialHandle),
                        reference = REFERENCE,
                    ),
                ).outcome
            }
            onInvocationElapsed(invocationElapsed)
            return outcome
        } finally {
            database.close()
        }
    }

    private fun source(
        headers: Map<String, String> = emptyMap(),
        session: EmbyPlaybackSession,
    ) = EmbyPlaybackSource(
        url = "https://provider.example/live.ts",
        headers = headers,
        mediaSourceId = "media-source",
        session = session,
    )

    private class PlaybackClient(
        private val source: EmbyPlaybackSource,
        private val closeRelease: CompletableDeferred<Unit>? = null,
    ) : EmbyCompatibleClient {
        val closeRequests = mutableListOf<CloseRequest>()
        private val closeObserved = CompletableDeferred<Unit>()

        suspend fun awaitClose() {
            withTimeout(2_000) {
                closeObserved.await()
            }
        }

        override suspend fun validate(
            baseUrl: String,
            requestedKind: ProviderKind,
            username: String,
            password: String,
        ): EmbyValidation = error("Not used")

        override suspend fun refreshChannels(
            account: ValidatedProviderAccount,
            accessToken: String,
        ): EmbyChannelRefresh = error("Not used")

        override suspend fun resolvePlayback(
            account: ValidatedProviderAccount,
            accessToken: String,
            reference: PlaybackReference,
            preferences: PlaybackPreferences,
        ): EmbyPlaybackSource = source

        override suspend fun closePlayback(
            account: ValidatedProviderAccount,
            accessToken: String,
            itemId: String,
            mediaSourceId: String?,
            session: EmbyPlaybackSession,
        ): Boolean {
            closeRequests += CloseRequest(
                itemId = itemId,
                mediaSourceId = mediaSourceId,
                session = session,
            )
            closeObserved.complete(Unit)
            closeRelease?.await()
            return true
        }
    }

    private data class CloseRequest(
        val itemId: String,
        val mediaSourceId: String?,
        val session: EmbyPlaybackSession,
    )

    private companion object {
        val REFERENCE = PlaybackReference(
            providerId = EmbyCompatibleProvider.ID,
            itemId = "channel",
            sourceType = "live_tv",
        )
    }
}
