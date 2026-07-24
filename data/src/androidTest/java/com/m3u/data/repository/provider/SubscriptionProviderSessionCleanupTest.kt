package com.m3u.data.repository.provider

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import com.m3u.data.database.M3UDatabase
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.ChannelPlaybackReference
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.ProviderAccount
import com.m3u.data.database.model.ProviderCredentialEntity
import com.m3u.data.database.model.ProviderPlaybackSessionEntity
import com.m3u.data.extension.SubscriptionProviderImporter
import com.m3u.data.extension.security.ActiveExtensionPrincipalRegistry
import com.m3u.data.extension.security.CredentialVault
import com.m3u.data.extension.security.ExtensionPrincipal
import com.m3u.data.extension.security.ProviderBrokerScopeStore
import com.m3u.data.repository.extension.ExtensionContributionScheduler
import com.m3u.data.repository.extension.ExtensionContributionRunCoordinator
import com.m3u.data.worker.ProviderSessionCleanupWorker
import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionCallContext
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionCapabilityRequest
import com.m3u.extension.api.ExtensionEntrypoint
import com.m3u.extension.api.ExtensionError
import com.m3u.extension.api.ExtensionHandler
import com.m3u.extension.api.ExtensionHookDeclaration
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionSemanticVersion
import com.m3u.extension.api.HookResult
import com.m3u.extension.api.InvocationId
import com.m3u.extension.api.SerializedExtensionEnvelope
import com.m3u.extension.api.SerializedExtensionResult
import com.m3u.extension.api.security.BrokerValue
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.api.security.SecretReference
import com.m3u.extension.api.subscription.PlaybackHeaderValue
import com.m3u.extension.api.subscription.PlaybackSessionCloseReason
import com.m3u.extension.api.subscription.PlaybackSessionCloseRequest
import com.m3u.extension.api.subscription.PlaybackSessionCloseResult
import com.m3u.extension.api.subscription.PlaybackSessionDescriptor
import com.m3u.extension.api.subscription.PlaybackSourceResolveRequest
import com.m3u.extension.api.subscription.PlaybackSourceResolveResult
import com.m3u.extension.api.subscription.PlaybackReference
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.api.subscription.SubscriptionChannelDescriptor
import com.m3u.extension.api.subscription.SubscriptionContentRefreshRequest
import com.m3u.extension.api.subscription.SubscriptionContentRefreshResult
import com.m3u.extension.api.subscription.SubscriptionProviderErrorCodes
import com.m3u.extension.api.subscription.SubscriptionHookSpecs
import com.m3u.extension.api.subscription.SubscriptionSourceDescriptor
import com.m3u.extension.runtime.ExtensionRegistrationResult
import com.m3u.extension.runtime.ExtensionRuntime
import com.m3u.extension.runtime.ExtensionTransport
import com.m3u.extension.runtime.ExtensionTransportHealth
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubscriptionProviderSessionCleanupTest {
    @Test
    fun restoredCredentialThatCannotBeDecryptedRequiresReauthentication() = runBlocking {
        withFixture { fixture ->
            fixture.seedAccountAndCredential()
            fixture.credentialVault.decryptable = false

            assertEquals(1, fixture.repository.invalidateUndecryptableCredentials())
            assertNull(fixture.database.providerDao().getCredential(ACCOUNT_ID))
            assertTrue(
                requireNotNull(
                    fixture.database.providerDao().getAccount(ACCOUNT_ID)
                ).requiresReauthentication
            )
            assertEquals(0, fixture.repository.invalidateUndecryptableCredentials())
        }
    }

    @Test
    fun refreshImmediatelyInvalidatesCredentialWhenKeystoreMaterialIsUnavailable() = runBlocking {
        withFixture { fixture ->
            fixture.seedAccountAndCredential()
            fixture.seedPlaybackSession()
            fixture.credentialVault.decryptable = false

            try {
                fixture.repository.refresh(PLAYLIST_URL)
                fail("Expected the unavailable provider credential to be rejected")
            } catch (_: ProviderOperationException) {
                // Expected.
            }

            assertNull(fixture.database.providerDao().getCredential(ACCOUNT_ID))
            assertTrue(
                requireNotNull(
                    fixture.database.providerDao().getAccount(ACCOUNT_ID)
                ).requiresReauthentication
            )
            assertTrue(fixture.database.providerDao().getPlaybackSessions().isEmpty())
            assertTrue(fixture.extension.refreshRequests.isEmpty())
        }
    }

    @Test
    fun refreshAuthenticationFailureInvalidatesCredentialAndDiscardsUnclosableSessions() =
        runBlocking {
            withFixture(refreshAuthenticationFailure = true) { fixture ->
                fixture.seedAccountAndCredential()
                fixture.seedPlaybackSession()

                val error = try {
                    fixture.repository.refresh(PLAYLIST_URL)
                    fail("Expected provider authentication to fail")
                    error("Unreachable")
                } catch (error: ProviderOperationException) {
                    error
                }

                assertEquals(SubscriptionProviderErrorCodes.AuthenticationFailed.value, error.code)
                assertNull(fixture.database.providerDao().getCredential(ACCOUNT_ID))
                assertTrue(
                    requireNotNull(
                        fixture.database.providerDao().getAccount(ACCOUNT_ID)
                    ).requiresReauthentication
                )
                assertTrue(fixture.database.providerDao().getPlaybackSessions().isEmpty())
            }
        }

    @Test
    fun pendingPlaybackSessionDoesNotBlockRefresh() = runBlocking {
        withFixture { fixture ->
            fixture.seedAccountAndCredential()
            fixture.seedPlaybackSession()

            val refreshed = fixture.repository.refresh(PLAYLIST_URL)

            assertEquals(1, refreshed.channelCount)
            assertEquals(1, fixture.extension.refreshRequests.size)
            assertNotNull(fixture.database.providerDao().getPlaybackSession(SESSION_ID))
        }
    }

    @Test
    fun resolveAuthenticationFailureInvalidatesCredential() = runBlocking {
        withFixture(resolveAuthenticationFailure = true) { fixture ->
            fixture.seedAccountAndCredential()
            val channelId = fixture.seedPlaybackReference()

            val error = try {
                fixture.repository.resolvePlayback(channelId)
                fail("Expected provider authentication to fail")
                error("Unreachable")
            } catch (error: ProviderOperationException) {
                error
            }

            assertEquals(SubscriptionProviderErrorCodes.AuthenticationFailed.value, error.code)
            assertNull(fixture.database.providerDao().getCredential(ACCOUNT_ID))
            assertTrue(
                requireNotNull(
                    fixture.database.providerDao().getAccount(ACCOUNT_ID)
                ).requiresReauthentication
            )
        }
    }

    @Test
    fun closeAuthenticationFailureInvalidatesCredentialAndMakesRepeatCloseIdempotent() =
        runBlocking {
            withFixture(
                closeAuthenticationFailure = true,
                resolveResult = VALID_PLAYBACK_RESULT.copy(
                    session = PlaybackSessionDescriptor(
                        playSessionId = REMOTE_PLAY_SESSION_ID,
                        liveStreamId = REMOTE_LIVE_STREAM_ID,
                    )
                ),
            ) { fixture ->
                fixture.seedAccountAndCredential()
                val channelId = fixture.seedPlaybackReference()
                val session = requireNotNull(fixture.repository.resolvePlayback(channelId)?.session)

                val error = try {
                    fixture.repository.closePlayback(
                        session = session,
                        reason = ProviderPlaybackCloseReason.STOPPED,
                    )
                    fail("Expected provider authentication to fail")
                    error("Unreachable")
                } catch (error: ProviderOperationException) {
                    error
                }

                assertEquals(SubscriptionProviderErrorCodes.AuthenticationFailed.value, error.code)
                assertNull(fixture.database.providerDao().getCredential(ACCOUNT_ID))
                assertTrue(fixture.database.providerDao().getPlaybackSessions().isEmpty())
                assertTrue(
                    fixture.repository.closePlayback(
                        session = session,
                        reason = ProviderPlaybackCloseReason.STOPPED,
                    )
                )
                assertEquals(1, fixture.extension.closeRequests.size)
            }
        }

    @Test
    fun closeRejectsFabricatedSessionSharingAPersistedId() = runBlocking {
        withFixture { fixture ->
            fixture.seedAccountAndCredential()
            fixture.seedPlaybackSession()
            val fabricated = TEST_SESSION.toRepositoryModel().copy(itemId = "different-item")

            try {
                fixture.repository.closePlayback(
                    session = fabricated,
                    reason = ProviderPlaybackCloseReason.STOPPED,
                )
                fail("Expected the fabricated session to be rejected")
            } catch (_: ProviderOperationException) {
                // Expected.
            }

            assertTrue(fixture.extension.closeRequests.isEmpty())
            assertNotNull(fixture.database.providerDao().getPlaybackSession(SESSION_ID))
        }
    }

    @Test
    fun activeSessionCloseRejectsChangedAccountSnapshot() = runBlocking {
        withFixture(
            resolveResult = VALID_PLAYBACK_RESULT.copy(
                session = PlaybackSessionDescriptor(playSessionId = REMOTE_PLAY_SESSION_ID)
            )
        ) { fixture ->
            fixture.seedAccountAndCredential()
            val channelId = fixture.seedPlaybackReference()
            val session = requireNotNull(fixture.repository.resolvePlayback(channelId)?.session)
            fixture.database.providerDao().insertOrReplace(
                TEST_ACCOUNT.copy(baseUrl = "https://changed.example.test")
            )

            val error = try {
                fixture.repository.closePlayback(
                    session = session,
                    reason = ProviderPlaybackCloseReason.STOPPED,
                )
                fail("Expected the changed account to be rejected")
                error("Unreachable")
            } catch (error: ProviderOperationException) {
                error
            }

            assertEquals("provider.account_changed", error.code)
            assertTrue(fixture.extension.closeRequests.isEmpty())
            assertNotNull(fixture.database.providerDao().getPlaybackSession(session.id))
        }
    }

    @Test
    fun activeSessionCloseRejectsChangedCredentialCiphertext() = runBlocking {
        withFixture(
            resolveResult = VALID_PLAYBACK_RESULT.copy(
                session = PlaybackSessionDescriptor(playSessionId = REMOTE_PLAY_SESSION_ID)
            )
        ) { fixture ->
            fixture.seedAccountAndCredential()
            val channelId = fixture.seedPlaybackReference()
            val session = requireNotNull(fixture.repository.resolvePlayback(channelId)?.session)
            fixture.database.providerDao().insertOrReplace(
                fixture.credentialVault.encrypt(
                    accountId = ACCOUNT_ID,
                    secret = "replacement-token",
                    credentialHandle = "persistent:$ACCOUNT_ID",
                )
            )

            val error = try {
                fixture.repository.closePlayback(
                    session = session,
                    reason = ProviderPlaybackCloseReason.STOPPED,
                )
                fail("Expected the changed credential to be rejected")
                error("Unreachable")
            } catch (error: ProviderOperationException) {
                error
            }

            assertEquals("provider.account_changed", error.code)
            assertTrue(fixture.extension.closeRequests.isEmpty())
            assertNotNull(fixture.database.providerDao().getPlaybackSession(session.id))
        }
    }

    @Test
    fun orphanCloseSuccessDeletesSessionAndReportsClosed() = runBlocking {
        withFixture(closeResult = true) { fixture ->
            fixture.seedAccountAndCredential()
            fixture.seedPlaybackSession()

            val result = fixture.repository.closeOrphanedPlaybackSessions()

            assertEquals(
                ProviderSessionCleanupResult(
                    closedCount = 1,
                    pendingCount = 0,
                    recoverablePendingCount = 0,
                ),
                result,
            )
            assertTrue(fixture.database.providerDao().getPlaybackSessions().isEmpty())
            assertEquals(
                PlaybackSessionCloseReason.Recovery,
                fixture.extension.closeRequests.single().reason,
            )
        }
    }

    @Test
    fun corruptSessionsAreDeletedBeforeTheBoundedCleanupBatchIsSelected() = runBlocking {
        withFixture(closeResult = true) { fixture ->
            fixture.seedAccountAndCredential()
            repeat(128) { index ->
                fixture.seedPlaybackSession(
                    TEST_SESSION.copy(
                        id = "corrupt-session-$index",
                        providerId = if (index % 2 == 0) OTHER_PROVIDER_ID else EXTENSION_ID.value,
                        sourceType = if (index % 2 == 0) SOURCE_TYPE else "",
                        createdAtEpochMillis = 0L,
                    )
                )
            }
            fixture.seedPlaybackSession()

            val result = fixture.repository.closeOrphanedPlaybackSessions()

            assertEquals(
                ProviderSessionCleanupResult(
                    closedCount = 1,
                    pendingCount = 0,
                    recoverablePendingCount = 0,
                ),
                result,
            )
            assertTrue(fixture.database.providerDao().getPlaybackSessions().isEmpty())
            assertEquals(
                REMOTE_PLAY_SESSION_ID,
                fixture.extension.closeRequests.single().session.playSessionId,
            )
        }
    }

    @Test
    fun invalidCursorFieldsAreDeletedWithoutReturningAContinuation() = runBlocking {
        withFixture(closeResult = true) { fixture ->
            fixture.seedAccountAndCredential()
            fixture.seedPlaybackSession(
                TEST_SESSION.copy(
                    id = "",
                    createdAtEpochMillis = 0L,
                )
            )
            repeat(127) { index ->
                fixture.seedPlaybackSession(
                    TEST_SESSION.copy(
                        id = "negative-session-${index.toString().padStart(3, '0')}",
                        createdAtEpochMillis = -1L,
                    )
                )
            }

            val result = fixture.repository.closeOrphanedPlaybackSessions()

            assertEquals(
                ProviderSessionCleanupResult(
                    closedCount = 0,
                    pendingCount = 0,
                    recoverablePendingCount = 0,
                ),
                result,
            )
            assertTrue(fixture.database.providerDao().getPlaybackSessions().isEmpty())
            assertTrue(fixture.extension.closeRequests.isEmpty())
        }
    }

    @Test
    fun orphanCleanupReturnsCursorAndProcessesRowsBeyondFirstBatch() = runBlocking {
        withFixture(closeResult = true) { fixture ->
            fixture.seedAccountAndCredential()
            repeat(129) { index ->
                fixture.seedPlaybackSession(
                    TEST_SESSION.copy(
                        id = "session-${index.toString().padStart(3, '0')}",
                        playSessionId = "remote-session-$index",
                        createdAtEpochMillis = 1L,
                    )
                )
            }

            val first = fixture.repository.closeOrphanedPlaybackSessions()

            assertEquals(128, first.closedCount)
            assertEquals(1L, first.continuationCreatedAtEpochMillis)
            assertEquals("session-127", first.continuationSessionId)
            assertEquals(1, fixture.database.providerDao().countPlaybackSessions())

            val second = fixture.repository.closeOrphanedPlaybackSessions(
                afterCreatedAtEpochMillis = first.continuationCreatedAtEpochMillis,
                afterSessionId = first.continuationSessionId,
            )

            assertEquals(1, second.closedCount)
            assertEquals(null, second.continuationCreatedAtEpochMillis)
            assertTrue(fixture.database.providerDao().getPlaybackSessions().isEmpty())
        }
    }

    @Test
    fun cleanupContinuationDoesNotCloseSessionCreatedActiveAfterPreviousPage() = runBlocking {
        withFixture(
            closeResult = true,
            resolveResult = VALID_PLAYBACK_RESULT.copy(
                session = PlaybackSessionDescriptor(playSessionId = REMOTE_PLAY_SESSION_ID)
            ),
        ) { fixture ->
            fixture.seedAccountAndCredential()
            repeat(128) { index ->
                fixture.seedPlaybackSession(
                    TEST_SESSION.copy(
                        id = "orphan-${index.toString().padStart(3, '0')}",
                        playSessionId = "remote-orphan-$index",
                        createdAtEpochMillis = 1L,
                    )
                )
            }
            val first = fixture.repository.closeOrphanedPlaybackSessions()
            assertEquals(128, first.closedCount)

            val channelId = fixture.seedPlaybackReference()
            val activeSession = requireNotNull(
                fixture.repository.resolvePlayback(channelId)?.session
            )
            val continuation = fixture.repository.closeOrphanedPlaybackSessions(
                afterCreatedAtEpochMillis = first.continuationCreatedAtEpochMillis,
                afterSessionId = first.continuationSessionId,
            )

            assertEquals(0, continuation.closedCount)
            assertEquals(0, continuation.pendingCount)
            assertEquals(
                listOf(activeSession.id),
                fixture.database.providerDao().getPlaybackSessions().map { session -> session.id },
            )
            assertEquals(128, fixture.extension.closeRequests.size)

            assertTrue(
                fixture.repository.closePlayback(
                    session = activeSession,
                    reason = ProviderPlaybackCloseReason.STOPPED,
                )
            )
        }
    }

    @Test
    fun cleanupIncludesFutureDatedTombstoneAfterWallClockRollback() = runBlocking {
        withFixture(closeResult = true) { fixture ->
            fixture.seedAccountAndCredential()
            fixture.seedPlaybackSession(
                TEST_SESSION.copy(
                    createdAtEpochMillis = Long.MAX_VALUE,
                )
            )

            val result = fixture.repository.closeOrphanedPlaybackSessions()

            assertEquals(
                ProviderSessionCleanupResult(
                    closedCount = 1,
                    pendingCount = 0,
                    recoverablePendingCount = 0,
                ),
                result,
            )
            assertTrue(fixture.database.providerDao().getPlaybackSessions().isEmpty())
            assertEquals(
                REMOTE_PLAY_SESSION_ID,
                fixture.extension.closeRequests.single().session.playSessionId,
            )
        }
    }

    @Test
    fun startupCleanupSkipsSessionStillActiveInCurrentProcess() = runBlocking {
        withFixture(
            closeResult = true,
            resolveResult = VALID_PLAYBACK_RESULT.copy(
                session = PlaybackSessionDescriptor(
                    playSessionId = REMOTE_PLAY_SESSION_ID,
                    liveStreamId = REMOTE_LIVE_STREAM_ID,
                )
            ),
        ) { fixture ->
            fixture.seedAccountAndCredential()
            val channelId = fixture.seedPlaybackReference()
            val activeSession = requireNotNull(
                fixture.repository.resolvePlayback(channelId)?.session
            )

            val result = fixture.repository.closeOrphanedPlaybackSessions()

            assertEquals(
                ProviderSessionCleanupResult(
                    closedCount = 0,
                    pendingCount = 0,
                    recoverablePendingCount = 0,
                ),
                result,
            )
            assertEquals(
                listOf(activeSession.id),
                fixture.database.providerDao().getPlaybackSessions().map { session -> session.id },
            )
            assertTrue(fixture.extension.closeRequests.isEmpty())

            assertTrue(
                fixture.repository.closePlayback(
                    session = activeSession,
                    reason = ProviderPlaybackCloseReason.STOPPED,
                )
            )
        }
    }

    @Test
    fun orphanCloseNotAcknowledgedKeepsSessionAndReportsRecoverablePending() = runBlocking {
        withFixture(closeResult = false) { fixture ->
            fixture.seedAccountAndCredential()
            fixture.seedPlaybackSession()

            val result = fixture.repository.closeOrphanedPlaybackSessions()

            assertEquals(
                ProviderSessionCleanupResult(
                    closedCount = 0,
                    pendingCount = 1,
                    recoverablePendingCount = 1,
                ),
                result,
            )
            assertEquals(
                listOf(SESSION_ID),
                fixture.database.providerDao().getPlaybackSessions().map { session -> session.id },
            )
            assertEquals(
                PlaybackSessionCloseReason.Recovery,
                fixture.extension.closeRequests.single().reason,
            )
        }
    }

    @Test
    fun invalidPlaybackResultClosesReturnedSessionWithoutPersistingIt() = runBlocking {
        val invalidResult = PlaybackSourceResolveResult(
            url = "not-a-playback-url",
            session = PlaybackSessionDescriptor(
                playSessionId = REMOTE_PLAY_SESSION_ID,
                liveStreamId = REMOTE_LIVE_STREAM_ID,
            ),
        )
        withFixture(closeResult = true, resolveResult = invalidResult) { fixture ->
            fixture.seedAccountAndCredential()
            val channelId = fixture.seedPlaybackReference()

            try {
                fixture.repository.resolvePlayback(channelId)
                fail("Expected the invalid playback URL to be rejected")
            } catch (_: ProviderOperationException) {
                // Expected: the original validation failure remains visible after cleanup.
            }

            val closeRequest = fixture.extension.closeRequests.single()
            assertEquals(PlaybackSessionCloseReason.PlaybackFailed, closeRequest.reason)
            assertEquals(REMOTE_PLAY_SESSION_ID, closeRequest.session.playSessionId)
            assertEquals(REMOTE_LIVE_STREAM_ID, closeRequest.session.liveStreamId)
            assertEquals(ITEM_ID, closeRequest.reference.itemId)
            assertTrue(fixture.database.providerDao().getPlaybackSessions().isEmpty())
        }
    }

    @Test
    fun invalidPlaybackResultWhoseCloseIsUnconfirmedPersistsSessionForRecovery() = runBlocking {
        val invalidResult = PlaybackSourceResolveResult(
            url = "not-a-playback-url",
            session = PlaybackSessionDescriptor(
                playSessionId = REMOTE_PLAY_SESSION_ID,
                liveStreamId = REMOTE_LIVE_STREAM_ID,
            ),
        )
        withFixture(closeResult = false, resolveResult = invalidResult) { fixture ->
            fixture.seedAccountAndCredential()
            val channelId = fixture.seedPlaybackReference()

            try {
                fixture.repository.resolvePlayback(channelId)
                fail("Expected the invalid playback URL to be rejected")
            } catch (_: ProviderOperationException) {
                // Expected.
            }

            assertEquals(
                PlaybackSessionCloseReason.PlaybackFailed,
                fixture.extension.closeRequests.single().reason,
            )
            val pendingSession = fixture.database.providerDao().getPlaybackSessions().single()
            assertEquals(ACCOUNT_ID, pendingSession.accountId)
            assertEquals(REMOTE_PLAY_SESSION_ID, pendingSession.playSessionId)
            assertEquals(REMOTE_LIVE_STREAM_ID, pendingSession.liveStreamId)
        }
    }

    @Test
    fun cancellationAfterResolveReturnsPromptlyAndEnqueuesPersistedSessionCleanup() = runBlocking {
        val credentialHandle = CredentialHandle("persistent:$ACCOUNT_ID")
        val resolvedWithCredentialHeader = PlaybackSourceResolveResult(
            url = "https://media.example.test/live.m3u8",
            headers = mapOf(
                "Authorization" to PlaybackHeaderValue(
                    parts = listOf(
                        BrokerValue.Secret(
                            SecretReference(credentialHandle)
                        )
                    )
                )
            ),
            session = PlaybackSessionDescriptor(
                playSessionId = REMOTE_PLAY_SESSION_ID,
                liveStreamId = REMOTE_LIVE_STREAM_ID,
            ),
        )
        val closeRelease = CompletableDeferred<Unit>()
        withFixture(
            closeResult = false,
            resolveResult = resolvedWithCredentialHeader,
            beforeCloseResult = {
                closeRelease.await()
            },
        ) { fixture ->
            fixture.seedAccountAndCredential()
            val channelId = fixture.seedPlaybackReference()
            val context = ApplicationProvider.getApplicationContext<Context>()
            val workManager = WorkManager.getInstance(context)
            val workIdsBeforeCancellation = cleanupWorkIds(workManager)

            val resolve = async {
                val resolveJob = checkNotNull(currentCoroutineContext()[Job])
                fixture.credentialVault.beforeDecryptCall = 2 to {
                    resolveJob.cancel(CancellationException("Cancel playback acceptance"))
                }
                fixture.repository.resolvePlayback(channelId)
            }
            val cancellationReturnedPromptly = withTimeoutOrNull(1_000) {
                resolve.join()
                true
            } ?: false
            closeRelease.complete(Unit)
            try {
                resolve.await()
                fail("Expected playback acceptance to be cancelled")
            } catch (_: CancellationException) {
                // Expected.
            }

            assertTrue(cancellationReturnedPromptly)
            assertTrue(fixture.extension.closeRequests.isEmpty())
            val pendingSession = fixture.database.providerDao().getPlaybackSessions().single()
            assertEquals(REMOTE_PLAY_SESSION_ID, pendingSession.playSessionId)
            val enqueuedWorkIds = withTimeout(2_000) {
                var newWorkIds: Set<UUID>
                do {
                    newWorkIds = cleanupWorkIds(workManager) - workIdsBeforeCancellation
                    if (newWorkIds.isEmpty()) delay(20)
                } while (newWorkIds.isEmpty())
                newWorkIds
            }
            withContext(Dispatchers.IO) {
                enqueuedWorkIds.forEach { workId ->
                    workManager.cancelWorkById(workId).result.get()
                }
            }
        }
    }

    @Test
    fun externalDisableDuringHeaderValidationKeepsTombstoneAndOriginalError() = runBlocking {
        val resultWithInvalidHeader = PlaybackSourceResolveResult(
            url = "https://media.example.test/live.m3u8",
            headers = mapOf(
                "Host" to PlaybackHeaderValue(
                    parts = listOf(
                        BrokerValue.Secret(
                            SecretReference(CredentialHandle("persistent:$ACCOUNT_ID"))
                        )
                    )
                )
            ),
            session = PlaybackSessionDescriptor(playSessionId = REMOTE_PLAY_SESSION_ID),
        )
        withFixture(
            externalPrincipal = EXTERNAL_PRINCIPAL,
            resolveResult = resultWithInvalidHeader,
        ) { fixture ->
            fixture.seedAccountAndCredential()
            val channelId = fixture.seedPlaybackReference()
            fixture.credentialVault.beforeDecryptCall = 4 to {
                fixture.deactivateExternalPrincipal()
            }

            val error = try {
                fixture.repository.resolvePlayback(channelId)
                fail("Expected the transport-owned header to be rejected")
                error("Unreachable")
            } catch (error: ProviderOperationException) {
                error
            }

            assertEquals("Provider cannot set a transport-owned header", error.message)
            assertNull(error.code)
            assertTrue(fixture.extension.closeRequests.isEmpty())
            val tombstone = fixture.database.providerDao().getPlaybackSessions().single()
            assertEquals(REMOTE_PLAY_SESSION_ID, tombstone.playSessionId)
        }
    }

    @Test
    fun externalLeaseInvalidationDuringHeaderResolutionKeepsTombstoneAndCancellation() =
        runBlocking {
            val resultWithCredentialHeader = PlaybackSourceResolveResult(
                url = "https://media.example.test/live.m3u8",
                headers = mapOf(
                    "Authorization" to PlaybackHeaderValue(
                        parts = listOf(
                            BrokerValue.Secret(
                                SecretReference(CredentialHandle("persistent:$ACCOUNT_ID"))
                            )
                        )
                    )
                ),
                session = PlaybackSessionDescriptor(playSessionId = REMOTE_PLAY_SESSION_ID),
            )
            withFixture(
                externalPrincipal = EXTERNAL_PRINCIPAL,
                resolveResult = resultWithCredentialHeader,
            ) { fixture ->
                fixture.seedAccountAndCredential()
                val channelId = fixture.seedPlaybackReference()
                val resolving = async {
                    val resolveJob = checkNotNull(currentCoroutineContext()[Job])
                    fixture.credentialVault.beforeDecryptCall = 4 to {
                        fixture.deactivateExternalPrincipal()
                        resolveJob.cancel(CancellationException(CANCEL_MESSAGE))
                    }
                    fixture.repository.resolvePlayback(channelId)
                }

                val cancellation = try {
                    resolving.await()
                    fail("Expected playback acceptance to be cancelled")
                    error("Unreachable")
                } catch (error: CancellationException) {
                    error
                }

                assertEquals(CANCEL_MESSAGE, cancellation.message)
                assertTrue(fixture.extension.closeRequests.isEmpty())
                val tombstone = fixture.database.providerDao().getPlaybackSessions().single()
                assertEquals(REMOTE_PLAY_SESSION_ID, tombstone.playSessionId)
            }
        }

    @Test
    fun externalCloseConfirmationDeletesTombstoneAfterLeaseInvalidation() = runBlocking {
        val closeStarted = CompletableDeferred<Unit>()
        val allowCloseResult = CompletableDeferred<Unit>()
        withFixture(
            externalPrincipal = EXTERNAL_PRINCIPAL,
            resolveResult = VALID_PLAYBACK_RESULT.copy(
                session = PlaybackSessionDescriptor(playSessionId = REMOTE_PLAY_SESSION_ID)
            ),
            beforeCloseResult = {
                closeStarted.complete(Unit)
                allowCloseResult.await()
            },
        ) { fixture ->
            fixture.seedAccountAndCredential()
            val channelId = fixture.seedPlaybackReference()
            val session = requireNotNull(fixture.repository.resolvePlayback(channelId)?.session)
            val closing = async {
                fixture.repository.closePlayback(
                    session = session,
                    reason = ProviderPlaybackCloseReason.STOPPED,
                )
            }
            closeStarted.await()

            fixture.deactivateExternalPrincipal()
            allowCloseResult.complete(Unit)

            assertTrue(closing.await())
            assertNull(fixture.database.providerDao().getPlaybackSession(session.id))
        }
    }

    @Test
    fun removeAccountDeletesLocalDataWhenAnyRemoteCloseIsUnconfirmed() = runBlocking {
        withFixture(closeResults = listOf(true, false)) { fixture ->
            fixture.seedAccountAndCredential()
            fixture.seedPlaybackSession()
            fixture.seedPlaybackSession(
                TEST_SESSION.copy(
                    id = SECOND_SESSION_ID,
                    playSessionId = SECOND_REMOTE_PLAY_SESSION_ID,
                    createdAtEpochMillis = 2L,
                )
            )

            fixture.repository.removeAccount(PLAYLIST_URL)

            assertNull(fixture.database.providerDao().getAccount(ACCOUNT_ID))
            assertTrue(fixture.database.providerDao().getPlaybackSessions().isEmpty())
            assertEquals(2, fixture.extension.closeRequests.size)
        }
    }

    @Test
    fun removeAccountDeletesLocalDataWhenRemoteCloseThrows() = runBlocking {
        withFixture(closeFailure = IllegalStateException("Provider is offline")) { fixture ->
            fixture.seedAccountAndCredential()
            fixture.seedPlaybackSession()

            fixture.repository.removeAccount(PLAYLIST_URL)

            assertNull(fixture.database.providerDao().getAccount(ACCOUNT_ID))
            assertTrue(fixture.database.providerDao().getPlaybackSessions().isEmpty())
            assertEquals(1, fixture.extension.closeRequests.size)
        }
    }

    @Test
    fun removeAccountDeletesAccountAfterEverySessionCloseIsConfirmed() = runBlocking {
        withFixture(closeResults = listOf(true, true)) { fixture ->
            fixture.seedAccountAndCredential()
            fixture.seedPlaybackSession()
            fixture.seedPlaybackSession(
                TEST_SESSION.copy(
                    id = SECOND_SESSION_ID,
                    playSessionId = SECOND_REMOTE_PLAY_SESSION_ID,
                    createdAtEpochMillis = 2L,
                )
            )

            fixture.repository.removeAccount(PLAYLIST_URL)

            assertNull(fixture.database.providerDao().getAccount(ACCOUNT_ID))
            assertTrue(fixture.database.providerDao().getPlaybackSessions().isEmpty())
            assertEquals(2, fixture.extension.closeRequests.size)
        }
    }

    @Test
    fun localProviderRemovalRollsBackAccountChannelAndPlaylistTogether() = runBlocking {
        withFixture { fixture ->
            fixture.seedAccountAndCredential()
            val channelId = fixture.seedPlaybackReference()
            fixture.database.openHelper.writableDatabase.execSQL(
                """
                CREATE TRIGGER abort_test_provider_playlist_delete
                BEFORE DELETE ON playlists
                WHEN OLD.url = '$PLAYLIST_URL'
                BEGIN
                    SELECT RAISE(ABORT, 'simulated provider removal crash');
                END
                """.trimIndent()
            )

            try {
                fixture.repository.removeAccount(PLAYLIST_URL)
                fail("Expected the simulated final delete to abort the transaction")
            } catch (_: Exception) {
                // Expected: every local delete must roll back with the playlist delete.
            }

            assertNotNull(fixture.database.providerDao().getAccount(ACCOUNT_ID))
            assertNotNull(fixture.database.channelDao().get(channelId))
            assertNotNull(fixture.database.providerDao().getPlaybackReference(channelId))
            assertNotNull(fixture.database.playlistDao().get(PLAYLIST_URL))
        }
    }

    @Test
    fun removeAccountWaitsForInFlightRefreshThenDeletesRefreshedAccount() = runBlocking {
        val refreshStarted = CompletableDeferred<Unit>()
        val allowRefresh = CompletableDeferred<Unit>()
        withFixture(
            beforeRefreshResult = {
                refreshStarted.complete(Unit)
                allowRefresh.await()
            }
        ) { fixture ->
            fixture.seedAccountAndCredential()
            val refreshing = async { fixture.repository.refresh(PLAYLIST_URL) }
            refreshStarted.await()

            val removing = async(start = CoroutineStart.UNDISPATCHED) {
                fixture.repository.removeAccount(PLAYLIST_URL)
            }
            yield()
            assertFalse(removing.isCompleted)
            allowRefresh.complete(Unit)

            assertEquals(1, refreshing.await().channelCount)
            removing.await()
            assertNull(fixture.database.providerDao().getAccount(ACCOUNT_ID))
            assertTrue(fixture.database.providerDao().getPlaybackSessions().isEmpty())
            assertEquals(1, fixture.extension.refreshRequests.size)
        }
    }

    @Test
    fun refreshWaitingForRemovalDoesNotRecreateDeletedAccount() = runBlocking {
        val closeStarted = CompletableDeferred<Unit>()
        val allowClose = CompletableDeferred<Unit>()
        withFixture(
            beforeCloseResult = {
                closeStarted.complete(Unit)
                allowClose.await()
            }
        ) { fixture ->
            fixture.seedAccountAndCredential()
            fixture.seedPlaybackSession()
            val removing = async { fixture.repository.removeAccount(PLAYLIST_URL) }
            closeStarted.await()

            val refreshing = async(start = CoroutineStart.UNDISPATCHED) {
                try {
                    fixture.repository.refresh(PLAYLIST_URL)
                    null
                } catch (error: ProviderOperationException) {
                    error
                }
            }
            yield()
            assertFalse(refreshing.isCompleted)
            allowClose.complete(Unit)

            removing.await()
            assertEquals("Provider account was not found", refreshing.await()?.message)
            assertNull(fixture.database.providerDao().getAccount(ACCOUNT_ID))
            assertTrue(fixture.database.providerDao().getPlaybackSessions().isEmpty())
            assertTrue(fixture.extension.refreshRequests.isEmpty())
        }
    }

    @Test
    fun removeAccountWaitsForInFlightResolveAndClosesItsSession() = runBlocking {
        val resolveStarted = CompletableDeferred<Unit>()
        val allowResolve = CompletableDeferred<Unit>()
        withFixture(
            resolveResult = VALID_PLAYBACK_RESULT.copy(
                session = PlaybackSessionDescriptor(
                    playSessionId = REMOTE_PLAY_SESSION_ID,
                    liveStreamId = REMOTE_LIVE_STREAM_ID,
                )
            ),
            beforeResolveResult = {
                resolveStarted.complete(Unit)
                allowResolve.await()
            }
        ) { fixture ->
            fixture.seedAccountAndCredential()
            val channelId = fixture.seedPlaybackReference()
            val resolving = async { fixture.repository.resolvePlayback(channelId) }
            resolveStarted.await()

            val removing = async { fixture.repository.removeAccount(PLAYLIST_URL) }
            yield()
            assertFalse(removing.isCompleted)
            allowResolve.complete(Unit)

            assertNotNull(resolving.await()?.session)
            removing.await()
            assertNull(fixture.database.providerDao().getAccount(ACCOUNT_ID))
            assertTrue(fixture.database.providerDao().getPlaybackSessions().isEmpty())
            assertEquals(1, fixture.extension.closeRequests.size)
        }
    }

    @Test
    fun globalSessionCapacityReservationIsAtomicAcrossAccounts() = runBlocking {
        val resolveStarted = CompletableDeferred<Unit>()
        val allowResolve = CompletableDeferred<Unit>()
        val resultWithSession = VALID_PLAYBACK_RESULT.copy(
            session = PlaybackSessionDescriptor(playSessionId = REMOTE_PLAY_SESSION_ID)
        )
        withFixture(
            resolveResult = resultWithSession,
            beforeResolveResult = {
                resolveStarted.complete(Unit)
                allowResolve.await()
            },
        ) { fixture ->
            fixture.seedPlaybackSessionCapacity(63)
            val firstAccount = capacityTargetAccount("first")
            val secondAccount = capacityTargetAccount("second")
            fixture.seedAccountAndCredential(firstAccount)
            fixture.seedAccountAndCredential(secondAccount)
            val firstChannelId = fixture.seedPlaybackReference(firstAccount)
            val secondChannelId = fixture.seedPlaybackReference(secondAccount)

            val firstResolve = async {
                runCatching { fixture.repository.resolvePlayback(firstChannelId) }
            }
            resolveStarted.await()
            val secondResolve = async {
                runCatching { fixture.repository.resolvePlayback(secondChannelId) }
            }
            yield()
            allowResolve.complete(Unit)

            val outcomes = listOf(firstResolve.await(), secondResolve.await())
            assertEquals(1, outcomes.count { outcome -> outcome.isSuccess })
            val capacityFailure = outcomes.single { outcome -> outcome.isFailure }.exceptionOrNull()
            assertTrue(capacityFailure is ProviderOperationException)
            assertEquals(
                "provider.session_capacity_reached",
                (capacityFailure as ProviderOperationException).code,
            )
            assertEquals(64, fixture.database.providerDao().countPlaybackSessions())
        }
    }

    @Test
    fun failedRemoteResolveReleasesGlobalSessionCapacityReservation() = runBlocking {
        val attempts = AtomicInteger()
        val resultWithSession = VALID_PLAYBACK_RESULT.copy(
            session = PlaybackSessionDescriptor(playSessionId = REMOTE_PLAY_SESSION_ID)
        )
        withFixture(
            resolveResult = resultWithSession,
            beforeResolveResult = {
                if (attempts.incrementAndGet() == 1) {
                    throw IllegalStateException("Remote provider failed")
                }
            },
        ) { fixture ->
            fixture.seedPlaybackSessionCapacity(63)
            val account = capacityTargetAccount("failure")
            fixture.seedAccountAndCredential(account)
            val channelId = fixture.seedPlaybackReference(account)

            val firstFailure = runCatching {
                fixture.repository.resolvePlayback(channelId)
            }.exceptionOrNull()

            assertNotNull(firstFailure)
            assertEquals(63, fixture.database.providerDao().countPlaybackSessions())
            assertNotNull(fixture.repository.resolvePlayback(channelId)?.session)
            assertEquals(64, fixture.database.providerDao().countPlaybackSessions())
        }
    }

    @Test
    fun cancelledRemoteResolveReleasesGlobalSessionCapacityReservation() = runBlocking {
        val attempts = AtomicInteger()
        val resolveStarted = CompletableDeferred<Unit>()
        val holdFirstResolve = CompletableDeferred<Unit>()
        val resultWithSession = VALID_PLAYBACK_RESULT.copy(
            session = PlaybackSessionDescriptor(playSessionId = REMOTE_PLAY_SESSION_ID)
        )
        withFixture(
            resolveResult = resultWithSession,
            beforeResolveResult = {
                if (attempts.incrementAndGet() == 1) {
                    resolveStarted.complete(Unit)
                    holdFirstResolve.await()
                }
            },
        ) { fixture ->
            fixture.seedPlaybackSessionCapacity(63)
            val account = capacityTargetAccount("cancellation")
            fixture.seedAccountAndCredential(account)
            val channelId = fixture.seedPlaybackReference(account)
            val cancelledResolve = async {
                fixture.repository.resolvePlayback(channelId)
            }
            resolveStarted.await()

            cancelledResolve.cancel(CancellationException("Cancel remote resolve"))
            try {
                cancelledResolve.await()
                fail("Expected remote resolve to be cancelled")
            } catch (_: CancellationException) {
                // Expected.
            }

            assertEquals(63, fixture.database.providerDao().countPlaybackSessions())
            assertNotNull(fixture.repository.resolvePlayback(channelId)?.session)
            assertEquals(64, fixture.database.providerDao().countPlaybackSessions())
        }
    }

    private suspend fun withFixture(
        closeResult: Boolean = true,
        closeResults: List<Boolean> = listOf(closeResult),
        closeFailure: Throwable? = null,
        resolveResult: PlaybackSourceResolveResult = VALID_PLAYBACK_RESULT,
        refreshResult: SubscriptionContentRefreshResult = VALID_REFRESH_RESULT,
        beforeCloseResult: suspend () -> Unit = {},
        beforeResolveResult: suspend () -> Unit = {},
        beforeRefreshResult: suspend () -> Unit = {},
        closeAuthenticationFailure: Boolean = false,
        resolveAuthenticationFailure: Boolean = false,
        refreshAuthenticationFailure: Boolean = false,
        externalPrincipal: ExtensionPrincipal? = null,
        block: suspend (TestFixture) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, M3UDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val credentialVault = TestCredentialVault()
        val extension = TestProviderExtension(
            defaultCloseResult = closeResult,
            closeResults = closeResults,
            closeFailure = closeFailure,
            resolveResult = resolveResult,
            refreshResult = refreshResult,
            beforeCloseResult = beforeCloseResult,
            beforeResolveResult = beforeResolveResult,
            beforeRefreshResult = beforeRefreshResult,
            closeAuthenticationFailure = closeAuthenticationFailure,
            resolveAuthenticationFailure = resolveAuthenticationFailure,
            refreshAuthenticationFailure = refreshAuthenticationFailure,
        )
        val runtime = ExtensionRuntime(ExtensionApiVersions.Current)
        val registration = if (externalPrincipal == null) {
            runtime.register(extension)
        } else {
            runtime.register(TestExternalProviderTransport(extension))
        }
        assertTrue(registration is ExtensionRegistrationResult.Registered)
        if (externalPrincipal != null) {
            val registered = registration as ExtensionRegistrationResult.Registered
            runtime.recordTransportHealth(
                extension.manifest.id,
                checkNotNull(registered.registrationToken),
                ExtensionTransportHealth.HEALTHY,
            )
        }
        val principalRegistry = ActiveExtensionPrincipalRegistry().apply {
            externalPrincipal?.let(::activate)
        }
        val repository = SubscriptionProviderRepositoryImpl(
            context = context,
            runtime = runtime,
            providerDao = database.providerDao(),
            playlistDao = database.playlistDao(),
            importer = SubscriptionProviderImporter(
                database = database,
                playlistDao = database.playlistDao(),
                channelDao = database.channelDao(),
                providerDao = database.providerDao(),
                programmeDao = database.programmeDao(),
                credentialVault = credentialVault,
            ),
            credentialVault = credentialVault,
            extensionContributionScheduler = NoOpExtensionContributionScheduler,
            extensionContributionRunCoordinator = ExtensionContributionRunCoordinator(),
            activePrincipalRegistry = principalRegistry,
            providerBrokerScopeStore = ProviderBrokerScopeStore(
                credentialVault = credentialVault,
                principalRegistry = principalRegistry,
            ),
            lifecycleCoordinator = ProviderLifecycleCoordinator(),
        )
        try {
            block(
                TestFixture(
                    database = database,
                    repository = repository,
                    credentialVault = credentialVault,
                    extension = extension,
                    principalRegistry = principalRegistry,
                    externalPrincipal = externalPrincipal,
                )
            )
        } finally {
            database.close()
        }
    }

    private suspend fun cleanupWorkIds(
        workManager: WorkManager,
    ): Set<UUID> = withContext(Dispatchers.IO) {
        workManager.getWorkInfosByTag(ProviderSessionCleanupWorker.WORK_TAG)
            .get()
            .mapTo(mutableSetOf()) { info -> info.id }
    }

    private data class TestFixture(
        val database: M3UDatabase,
        val repository: SubscriptionProviderRepositoryImpl,
        val credentialVault: TestCredentialVault,
        val extension: TestProviderExtension,
        val principalRegistry: ActiveExtensionPrincipalRegistry,
        val externalPrincipal: ExtensionPrincipal?,
    ) {
        suspend fun seedAccountAndCredential(
            account: ProviderAccount = TEST_ACCOUNT,
        ) {
            database.playlistDao().insertOrReplace(
                Playlist(
                    title = "Test provider",
                    url = account.playlistUrl,
                    source = DataSource.Provider,
                )
            )
            database.providerDao().insertOrReplace(
                if (externalPrincipal == null) {
                    account
                } else {
                    account.copy(
                        ownerPackageName = externalPrincipal.packageName,
                        ownerServiceName = externalPrincipal.serviceName,
                        ownerCertificateSha256 = externalPrincipal.certificateSha256,
                    )
                }
            )
            database.providerDao().insertOrReplace(
                credentialVault.encrypt(
                    accountId = account.id,
                    secret = "test-token",
                )
            )
        }

        suspend fun seedPlaybackSession(
            session: ProviderPlaybackSessionEntity = TEST_SESSION,
        ) {
            database.providerDao().insertOrReplace(session)
        }

        suspend fun seedPlaybackReference(
            account: ProviderAccount = TEST_ACCOUNT,
            itemId: String = ITEM_ID,
        ): Int {
            val channelId = database.channelDao().insertOrReplace(
                Channel(
                    url = Channel.URL_DYNAMIC,
                    category = "Test",
                    title = "Test channel",
                    playlistUrl = account.playlistUrl,
                    relationId = itemId,
                )
            ).toInt()
            database.providerDao().insertOrReplace(
                ChannelPlaybackReference(
                    channelId = channelId,
                    accountId = account.id,
                    providerId = EXTENSION_ID.value,
                    itemId = itemId,
                    mediaSourceId = MEDIA_SOURCE_ID,
                    sourceType = SOURCE_TYPE,
                )
            )
            return channelId
        }

        suspend fun seedPlaybackSessionCapacity(count: Int) {
            require(count in 0..64)
            repeat((count + 7) / 8) { accountIndex ->
                val account = TEST_ACCOUNT.copy(
                    id = "capacity-filler-account-$accountIndex",
                    serverId = "capacity-filler-server-$accountIndex",
                    userId = "capacity-filler-user-$accountIndex",
                    playlistUrl = "m3u-provider://account/capacity-filler-$accountIndex/live",
                )
                seedAccountAndCredential(account)
                val accountSessionCount = minOf(8, count - accountIndex * 8)
                repeat(accountSessionCount) { sessionIndex ->
                    val globalIndex = accountIndex * 8 + sessionIndex
                    seedPlaybackSession(
                        TEST_SESSION.copy(
                            id = "capacity-filler-session-$globalIndex",
                            accountId = account.id,
                            playSessionId = "capacity-filler-remote-session-$globalIndex",
                            createdAtEpochMillis = globalIndex.toLong() + 1,
                        )
                    )
                }
            }
        }

        fun deactivateExternalPrincipal() {
            val principal = checkNotNull(externalPrincipal)
            assertEquals(
                principal,
                principalRegistry.deactivate(
                    extensionId = principal.extensionId,
                    packageName = principal.packageName,
                    serviceName = principal.serviceName,
                ),
            )
        }
    }

    private class TestProviderExtension(
        private val defaultCloseResult: Boolean,
        closeResults: List<Boolean>,
        private val closeFailure: Throwable?,
        private val resolveResult: PlaybackSourceResolveResult,
        private val refreshResult: SubscriptionContentRefreshResult,
        private val beforeCloseResult: suspend () -> Unit,
        private val beforeResolveResult: suspend () -> Unit,
        private val beforeRefreshResult: suspend () -> Unit,
        private val closeAuthenticationFailure: Boolean,
        private val resolveAuthenticationFailure: Boolean,
        private val refreshAuthenticationFailure: Boolean,
    ) : ExtensionEntrypoint {
        val closeRequests = mutableListOf<PlaybackSessionCloseRequest>()
        val refreshRequests = mutableListOf<SubscriptionContentRefreshRequest>()
        private val queuedCloseResults = closeResults.toMutableList()

        suspend fun resolve(
            request: PlaybackSourceResolveRequest,
        ): HookResult<PlaybackSourceResolveResult> {
            beforeResolveResult()
            if (resolveAuthenticationFailure) return authenticationFailure()
            return HookResult.Success(resolveResult)
        }

        suspend fun close(
            request: PlaybackSessionCloseRequest,
        ): HookResult<PlaybackSessionCloseResult> {
            closeRequests += request
            beforeCloseResult()
            if (closeAuthenticationFailure) return authenticationFailure()
            closeFailure?.let { error -> throw error }
            val closed = if (queuedCloseResults.isEmpty()) {
                defaultCloseResult
            } else {
                queuedCloseResults.removeAt(0)
            }
            return HookResult.Success(PlaybackSessionCloseResult(closed = closed))
        }

        suspend fun refresh(
            request: SubscriptionContentRefreshRequest,
        ): HookResult<SubscriptionContentRefreshResult> {
            refreshRequests += request
            beforeRefreshResult()
            if (refreshAuthenticationFailure) return authenticationFailure()
            return HookResult.Success(refreshResult)
        }

        override val manifest = ExtensionManifest(
            id = EXTENSION_ID,
            displayName = "Provider session test",
            extensionVersion = ExtensionSemanticVersion(1, 0, 0),
            apiRange = ExtensionApiRange(
                minimum = ExtensionApiVersions.Current,
                maximum = ExtensionApiVersions.Current,
            ),
            hooks = setOf(
                ExtensionHookDeclaration(
                    hook = SubscriptionHookSpecs.ResolvePlayback.hook,
                    schemaVersion = SubscriptionHookSpecs.ResolvePlayback.schemaVersion,
                    requiredCapabilities = PLAYBACK_CAPABILITIES,
                ),
                ExtensionHookDeclaration(
                    hook = SubscriptionHookSpecs.ClosePlayback.hook,
                    schemaVersion = SubscriptionHookSpecs.ClosePlayback.schemaVersion,
                    requiredCapabilities = PLAYBACK_CAPABILITIES,
                ),
                ExtensionHookDeclaration(
                    hook = SubscriptionHookSpecs.Refresh.hook,
                    schemaVersion = SubscriptionHookSpecs.Refresh.schemaVersion,
                    requiredCapabilities = REFRESH_CAPABILITIES,
                ),
            ),
            capabilities = (PLAYBACK_CAPABILITIES + REFRESH_CAPABILITIES)
                .mapTo(mutableSetOf()) { capability ->
                    ExtensionCapabilityRequest(
                        capability = capability,
                        reason = "Exercise provider playback session lifecycle",
                    )
                },
        )

        override val handlers: Collection<ExtensionHandler<*, *>> = listOf(
            object : ExtensionHandler<PlaybackSourceResolveRequest, PlaybackSourceResolveResult> {
                override val spec = SubscriptionHookSpecs.ResolvePlayback

                override suspend fun invoke(
                    context: ExtensionCallContext,
                    request: PlaybackSourceResolveRequest,
                ): HookResult<PlaybackSourceResolveResult> = resolve(request)
            },
            object : ExtensionHandler<PlaybackSessionCloseRequest, PlaybackSessionCloseResult> {
                override val spec = SubscriptionHookSpecs.ClosePlayback

                override suspend fun invoke(
                    context: ExtensionCallContext,
                    request: PlaybackSessionCloseRequest,
                ): HookResult<PlaybackSessionCloseResult> = close(request)
            },
            object : ExtensionHandler<SubscriptionContentRefreshRequest, SubscriptionContentRefreshResult> {
                override val spec = SubscriptionHookSpecs.Refresh

                override suspend fun invoke(
                    context: ExtensionCallContext,
                    request: SubscriptionContentRefreshRequest,
                ): HookResult<SubscriptionContentRefreshResult> = refresh(request)
            },
        )

        private fun <T : com.m3u.extension.api.ExtensionPayload> authenticationFailure():
            HookResult<T> = HookResult.Failure(
                ExtensionError(
                    code = SubscriptionProviderErrorCodes.AuthenticationFailed,
                    message = "Provider credentials were rejected",
                    recoverable = false,
                )
            )
    }

    private class TestExternalProviderTransport(
        private val extension: TestProviderExtension,
    ) : ExtensionTransport {
        override val manifest: ExtensionManifest = extension.manifest

        override suspend fun invoke(
            request: SerializedExtensionEnvelope,
        ): SerializedExtensionResult = when (request.hook) {
            SubscriptionHookSpecs.ResolvePlayback.hook -> when (
                val result = extension.resolve(
                    JSON.decodeFromJsonElement(
                        SubscriptionHookSpecs.ResolvePlayback.requestSerializer,
                        request.payload,
                    )
                )
            ) {
                is HookResult.Success -> request.success(
                    JSON.encodeToJsonElement(
                        SubscriptionHookSpecs.ResolvePlayback.responseSerializer,
                        result.payload,
                    )
                )

                is HookResult.Failure -> request.failure(result.error)
            }

            SubscriptionHookSpecs.ClosePlayback.hook -> when (
                val result = extension.close(
                    JSON.decodeFromJsonElement(
                        SubscriptionHookSpecs.ClosePlayback.requestSerializer,
                        request.payload,
                    )
                )
            ) {
                is HookResult.Success -> request.success(
                    JSON.encodeToJsonElement(
                        SubscriptionHookSpecs.ClosePlayback.responseSerializer,
                        result.payload,
                    )
                )

                is HookResult.Failure -> request.failure(result.error)
            }

            SubscriptionHookSpecs.Refresh.hook -> when (
                val result = extension.refresh(
                    JSON.decodeFromJsonElement(
                        SubscriptionHookSpecs.Refresh.requestSerializer,
                        request.payload,
                    )
                )
            ) {
                is HookResult.Success -> request.success(
                    JSON.encodeToJsonElement(
                        SubscriptionHookSpecs.Refresh.responseSerializer,
                        result.payload,
                    )
                )

                is HookResult.Failure -> request.failure(result.error)
            }

            else -> error("Unexpected Hook ${request.hook}")
        }

        override suspend fun cancel(invocationId: InvocationId) = Unit

        override suspend fun health(): ExtensionTransportHealth =
            ExtensionTransportHealth.HEALTHY

        private fun SerializedExtensionEnvelope.success(
            payload: kotlinx.serialization.json.JsonElement,
        ) = SerializedExtensionResult(
            invocationId = invocationId,
            extensionId = extensionId,
            hook = hook,
            schemaVersion = schemaVersion,
            payload = payload,
        )

        private fun SerializedExtensionEnvelope.failure(
            error: ExtensionError,
        ) = SerializedExtensionResult(
            invocationId = invocationId,
            extensionId = extensionId,
            hook = hook,
            schemaVersion = schemaVersion,
            error = error,
        )
    }

    private class TestCredentialVault : CredentialVault {
        var beforeDecryptCall: Pair<Int, () -> Unit>? = null
        var decryptable: Boolean = true
        private var decryptCallCount = 0

        override fun encrypt(
            accountId: String,
            secret: String,
            credentialHandle: String?,
        ) = ProviderCredentialEntity(
            accountId = accountId,
            credentialHandle = credentialHandle ?: "persistent:$accountId",
            ciphertext = secret,
            nonce = "test-nonce",
            keyVersion = 1,
        )

        override fun decrypt(credential: ProviderCredentialEntity): String? {
            decryptCallCount++
            beforeDecryptCall?.takeIf { (callNumber, _) ->
                callNumber == decryptCallCount
            }?.also { (_, callback) ->
                beforeDecryptCall = null
                callback()
            }
            return credential.ciphertext.takeIf { decryptable }
        }

        override fun stage(secret: String): CredentialHandle = error("Not used")
        override fun consume(handle: CredentialHandle): String? = error("Not used")
    }

    private data object NoOpExtensionContributionScheduler : ExtensionContributionScheduler {
        override suspend fun enqueue(playlistUrl: String) = Unit

        override suspend fun cancel(playlistUrl: String) = Unit
    }

    private companion object {
        val JSON = Json {
            ignoreUnknownKeys = true
        }
        val EXTENSION_ID = ExtensionId("com.m3u.test.provider.session")
        val PROVIDER_KIND = ProviderKind("test")
        val EXTERNAL_PRINCIPAL = ExtensionPrincipal(
            extensionId = EXTENSION_ID,
            packageName = "com.m3u.test.provider.session.external",
            serviceName = "com.m3u.test.provider.session.external.ProviderService",
            certificateSha256 = "11".repeat(32),
            uid = 10_001,
        )
        val BROKER_CAPABILITIES = setOf(
            ExtensionCapabilityIds.Network,
            ExtensionCapabilityIds.CredentialRead,
        )
        val PLAYBACK_CAPABILITIES =
            BROKER_CAPABILITIES + ExtensionCapabilityIds.PlaybackResolve
        val REFRESH_CAPABILITIES =
            BROKER_CAPABILITIES + ExtensionCapabilityIds.SubscriptionRead
        const val ACCOUNT_ID = "account-1"
        const val PLAYLIST_URL = "m3u-provider://account/account-1/live"
        const val ITEM_ID = "item-1"
        const val MEDIA_SOURCE_ID = "media-source-1"
        const val SOURCE_TYPE = "live"
        const val SESSION_ID = "session-1"
        const val SECOND_SESSION_ID = "session-2"
        const val REMOTE_PLAY_SESSION_ID = "remote-play-session-1"
        const val SECOND_REMOTE_PLAY_SESSION_ID = "remote-play-session-2"
        const val REMOTE_LIVE_STREAM_ID = "remote-live-stream-1"
        const val OTHER_PROVIDER_ID = "com.m3u.other.provider"
        const val CANCEL_MESSAGE = "Cancel after external lease invalidation"

        val TEST_ACCOUNT = ProviderAccount(
            id = ACCOUNT_ID,
            providerId = EXTENSION_ID.value,
            providerKind = PROVIDER_KIND.value,
            baseUrl = "https://media.example.test",
            serverId = "server-1",
            serverName = "Test server",
            serverVersion = "1.0",
            userId = "user-1",
            username = "test-user",
            playlistUrl = PLAYLIST_URL,
        )
        val TEST_SESSION = ProviderPlaybackSessionEntity(
            id = SESSION_ID,
            accountId = ACCOUNT_ID,
            providerId = EXTENSION_ID.value,
            itemId = ITEM_ID,
            mediaSourceId = MEDIA_SOURCE_ID,
            sourceType = SOURCE_TYPE,
            playSessionId = REMOTE_PLAY_SESSION_ID,
            liveStreamId = REMOTE_LIVE_STREAM_ID,
            createdAtEpochMillis = 1L,
        )

        fun capacityTargetAccount(suffix: String) = TEST_ACCOUNT.copy(
            id = "capacity-target-account-$suffix",
            serverId = "capacity-target-server-$suffix",
            userId = "capacity-target-user-$suffix",
            playlistUrl = "m3u-provider://account/capacity-target-$suffix/live",
        )

        fun ProviderPlaybackSessionEntity.toRepositoryModel() = ProviderPlaybackSession(
            id = id,
            accountId = accountId,
            providerId = providerId,
            itemId = itemId,
            mediaSourceId = mediaSourceId,
            sourceType = sourceType,
            playSessionId = playSessionId,
            liveStreamId = liveStreamId,
        )
        val VALID_PLAYBACK_RESULT = PlaybackSourceResolveResult(
            url = "https://media.example.test/live.m3u8",
        )
        val VALID_REFRESH_RESULT = SubscriptionContentRefreshResult(
            source = SubscriptionSourceDescriptor(
                remoteId = TEST_ACCOUNT.serverId,
                providerKind = PROVIDER_KIND,
            ),
            channels = listOf(
                SubscriptionChannelDescriptor(
                    remoteId = ITEM_ID,
                    title = "Refreshed channel",
                    category = "Test",
                    playbackReference = PlaybackReference(
                        providerId = EXTENSION_ID,
                        itemId = ITEM_ID,
                        mediaSourceId = MEDIA_SOURCE_ID,
                        sourceType = SOURCE_TYPE,
                    ),
                )
            ),
        )
    }
}
