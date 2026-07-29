package com.m3u.data.repository.provider

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m3u.data.database.M3UDatabase
import com.m3u.data.database.model.MediaKinds
import com.m3u.data.database.model.ProviderCredentialEntity
import com.m3u.data.extension.SubscriptionProviderImporter
import com.m3u.data.extension.security.ActiveExtensionPrincipalRegistry
import com.m3u.data.extension.security.CredentialVault
import com.m3u.data.extension.security.ProviderBrokerScopeStore
import com.m3u.data.repository.extension.ExtensionContributionRunCoordinator
import com.m3u.data.repository.extension.ExtensionContributionScheduler
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
import com.m3u.extension.api.ExtensionPayload
import com.m3u.extension.api.ExtensionSemanticVersion
import com.m3u.extension.api.ExtensionSettingField
import com.m3u.extension.api.ExtensionSettingSchema
import com.m3u.extension.api.ExtensionSettingType
import com.m3u.extension.api.Hook
import com.m3u.extension.api.HookResult
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.api.subscription.PlaybackPreferences
import com.m3u.extension.api.subscription.PlaybackReference
import com.m3u.extension.api.subscription.PlaybackSessionCloseRequest
import com.m3u.extension.api.subscription.PlaybackSessionCloseResult
import com.m3u.extension.api.subscription.PlaybackSessionUpdateRequest
import com.m3u.extension.api.subscription.PlaybackSessionUpdateResult
import com.m3u.extension.api.subscription.PlaybackSourceResolveRequest
import com.m3u.extension.api.subscription.PlaybackSourceResolveResult
import com.m3u.extension.api.subscription.PlaybackMethods
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.api.subscription.ProviderMediaKind
import com.m3u.extension.api.subscription.ProviderMediaKinds
import com.m3u.extension.api.subscription.ProviderValidationEvidence
import com.m3u.extension.api.subscription.SubscriptionChannelDescriptor
import com.m3u.extension.api.subscription.SubscriptionContentBrowseRequest
import com.m3u.extension.api.subscription.SubscriptionContentBrowseResult
import com.m3u.extension.api.subscription.SubscriptionContentItemDescriptor
import com.m3u.extension.api.subscription.SubscriptionContentRefreshRequest
import com.m3u.extension.api.subscription.SubscriptionContentRefreshResult
import com.m3u.extension.api.subscription.SubscriptionHookSpecs
import com.m3u.extension.api.subscription.SubscriptionProviderDescriptor
import com.m3u.extension.api.subscription.SubscriptionProviderDiscoverRequest
import com.m3u.extension.api.subscription.SubscriptionProviderDiscoverResult
import com.m3u.extension.api.subscription.SubscriptionProviderErrorCodes
import com.m3u.extension.api.subscription.SubscriptionProviderSettingKeys
import com.m3u.extension.api.subscription.SubscriptionProviderValidateRequest
import com.m3u.extension.api.subscription.SubscriptionProviderValidateResult
import com.m3u.extension.api.subscription.SubscriptionProviderVariant
import com.m3u.extension.api.subscription.SubscriptionSourceDescriptor
import com.m3u.extension.api.subscription.ValidatedProviderAccount
import com.m3u.extension.runtime.ExtensionRegistrationResult
import com.m3u.extension.runtime.ExtensionRuntime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubscriptionProviderBrowseRepositoryTest {
    @Test
    fun providerMissingBrowseOrPlaybackUpdateIsRejectedDuringDiscovery() {
        listOf(
            SubscriptionHookSpecs.Browse.hook,
            SubscriptionHookSpecs.UpdatePlayback.hook,
        ).forEach { omittedHook ->
            withFixture(omittedHook = omittedHook) { fixture ->
                val failure = runCatching {
                    fixture.repository.discoverProviders()
                }.exceptionOrNull()

                assertTrue(failure is ProviderDiscoveryException)
                assertEquals(1, (failure as ProviderDiscoveryException).failureCount)
                assertTrue(fixture.database.playlistDao().getAll().isEmpty())
            }
        }
    }

    @Test
    fun rootBrowsePagesAreImportedAndRefreshPreservesLocalState() =
        withFixture { fixture ->
            fixture.extension.browsePages = initialBrowsePages()

            val subscription = fixture.subscribe()

            assertEquals(3, subscription.channelCount)
            assertRootBrowseRequests(
                fixture.extension.browseRequests,
                expectedCursors = listOf(null, SECOND_PAGE_CURSOR),
            )
            val initialChannels = fixture.database.channelDao()
                .getByPlaylistUrl(subscription.playlistUrl)
            val initialMovie = initialChannels.single { channel ->
                channel.relationId == "$CATALOG_RELATION_PREFIX$MOVIE_ID"
            }
            val initialSeries = initialChannels.single { channel ->
                channel.relationId == "$CATALOG_RELATION_PREFIX$SERIES_ID"
            }
            assertEquals(MediaKinds.MOVIE, initialMovie.mediaKind)
            assertEquals("Original release", initialMovie.subtitle)
            assertEquals("Initial movie overview", initialMovie.overview)
            assertEquals(2024, initialMovie.productionYear)
            assertTrue(initialMovie.playable)
            assertFalse(initialMovie.browsable)
            assertEquals(MediaKinds.SERIES, initialSeries.mediaKind)
            assertFalse(initialSeries.playable)
            assertTrue(initialSeries.browsable)

            fixture.database.channelDao().favouriteOrUnfavourite(initialMovie.id, true)
            fixture.database.channelDao().updateSeen(initialMovie.id, 91L)
            fixture.database.channelDao().hide(initialSeries.id, true)
            fixture.extension.browseRequests.clear()
            fixture.extension.browsePages = refreshedBrowsePages()

            val refreshed = fixture.repository.refresh(subscription.playlistUrl)

            assertEquals(3, refreshed.channelCount)
            assertRootBrowseRequests(
                fixture.extension.browseRequests,
                expectedCursors = listOf(null, SECOND_PAGE_CURSOR),
            )
            val refreshedChannels = fixture.database.channelDao()
                .getByPlaylistUrl(subscription.playlistUrl)
            val refreshedMovie = refreshedChannels.single { channel ->
                channel.relationId == "$CATALOG_RELATION_PREFIX$MOVIE_ID"
            }
            val refreshedSeries = refreshedChannels.single { channel ->
                channel.relationId == "$CATALOG_RELATION_PREFIX$SERIES_ID"
            }
            assertEquals(initialMovie.id, refreshedMovie.id)
            assertEquals("Movie refreshed", refreshedMovie.title)
            assertEquals("Remastered", refreshedMovie.subtitle)
            assertEquals("Refreshed movie overview", refreshedMovie.overview)
            assertEquals(2025, refreshedMovie.productionYear)
            assertTrue(refreshedMovie.favourite)
            assertEquals(91L, refreshedMovie.seen)
            assertEquals(initialSeries.id, refreshedSeries.id)
            assertEquals("Series refreshed", refreshedSeries.title)
            assertTrue(refreshedSeries.hidden)
            assertEquals(
                "movie-source-refreshed",
                fixture.database.providerDao()
                    .getPlaybackReference(refreshedMovie.id)
                    ?.mediaSourceId,
            )
        }

    @Test
    fun playbackStartPositionIsForwardedWithoutDraftFallbacks() =
        withFixture { fixture ->
            fixture.extension.browsePages = initialBrowsePages()
            val subscription = fixture.subscribe()
            val movie = fixture.database.channelDao()
                .getByPlaylistUrl(subscription.playlistUrl)
                .single { channel ->
                    channel.relationId == "$CATALOG_RELATION_PREFIX$MOVIE_ID"
                }

            fixture.repository.resolvePlayback(
                channelId = movie.id,
                preferences = PlaybackPreferences(
                    maxStreamingBitrate = 8_000_000L,
                    allowTranscoding = false,
                    startPositionTicks = 123_000_000L,
                ),
            )

            assertEquals(
                PlaybackPreferences(
                    maxStreamingBitrate = 8_000_000L,
                    allowTranscoding = false,
                    startPositionTicks = 123_000_000L,
                ),
                fixture.extension.resolveRequests.single().preferences,
            )
        }

    private fun withFixture(
        omittedHook: Hook? = null,
        block: suspend (Fixture) -> Unit,
    ) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, M3UDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val credentialVault = TestCredentialVault()
        val extension = BrowseTestProviderExtension(
            credentialVault = credentialVault,
            omittedHook = omittedHook,
        )
        val runtime = ExtensionRuntime(ExtensionApiVersions.Current)
        assertTrue(runtime.register(extension) is ExtensionRegistrationResult.Registered)
        val principalRegistry = ActiveExtensionPrincipalRegistry()
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
                Fixture(
                    database = database,
                    repository = repository,
                    extension = extension,
                )
            )
        } finally {
            database.close()
        }
    }

    private data class Fixture(
        val database: M3UDatabase,
        val repository: SubscriptionProviderRepositoryImpl,
        val extension: BrowseTestProviderExtension,
    ) {
        suspend fun subscribe(): ProviderSubscriptionResult = repository.subscribe(
            ProviderSubscriptionRequest(
                title = "Browse provider",
                providerId = EXTENSION_ID,
                providerKind = PROVIDER_KIND,
                settingValues = mapOf(
                    SubscriptionProviderSettingKeys.BaseUrl to BASE_URL,
                ),
                credentialHandles = mapOf(
                    SubscriptionProviderSettingKeys.Password to
                        repository.stageCredential(PASSWORD),
                ),
            )
        )
    }

    private class BrowseTestProviderExtension(
        private val credentialVault: TestCredentialVault,
        private val omittedHook: Hook?,
    ) : ExtensionEntrypoint {
        var browsePages: Map<String?, SubscriptionContentBrowseResult> = emptyMap()
        val browseRequests = mutableListOf<SubscriptionContentBrowseRequest>()
        val resolveRequests = mutableListOf<PlaybackSourceResolveRequest>()

        override val manifest = ExtensionManifest(
            id = EXTENSION_ID,
            displayName = "Browse test provider",
            extensionVersion = ExtensionSemanticVersion(1, 0, 0),
            apiRange = ExtensionApiRange(
                minimum = ExtensionApiVersions.Current,
                maximum = ExtensionApiVersions.Current,
            ),
            hooks = buildSet {
                add(
                    ExtensionHookDeclaration(
                        hook = SubscriptionHookSpecs.Discover.hook,
                        schemaVersion = SubscriptionHookSpecs.Discover.schemaVersion,
                    )
                )
                add(
                    ExtensionHookDeclaration(
                        hook = SubscriptionHookSpecs.Validate.hook,
                        schemaVersion = SubscriptionHookSpecs.Validate.schemaVersion,
                        requiredCapabilities = setOf(
                            ExtensionCapabilityIds.Network,
                            ExtensionCapabilityIds.CredentialWrite,
                        ),
                    )
                )
                add(
                    ExtensionHookDeclaration(
                        hook = SubscriptionHookSpecs.Refresh.hook,
                        schemaVersion = SubscriptionHookSpecs.Refresh.schemaVersion,
                        requiredCapabilities = setOf(
                            ExtensionCapabilityIds.Network,
                            ExtensionCapabilityIds.CredentialRead,
                            ExtensionCapabilityIds.SubscriptionRead,
                        ),
                    )
                )
                if (omittedHook != SubscriptionHookSpecs.Browse.hook) {
                    add(
                        ExtensionHookDeclaration(
                            hook = SubscriptionHookSpecs.Browse.hook,
                            schemaVersion = SubscriptionHookSpecs.Browse.schemaVersion,
                            requiredCapabilities = setOf(
                                ExtensionCapabilityIds.Network,
                                ExtensionCapabilityIds.CredentialRead,
                                ExtensionCapabilityIds.SubscriptionRead,
                            ),
                        )
                    )
                }
                add(
                    ExtensionHookDeclaration(
                        hook = SubscriptionHookSpecs.ResolvePlayback.hook,
                        schemaVersion = SubscriptionHookSpecs.ResolvePlayback.schemaVersion,
                        requiredCapabilities = setOf(
                            ExtensionCapabilityIds.Network,
                            ExtensionCapabilityIds.CredentialRead,
                            ExtensionCapabilityIds.PlaybackResolve,
                        ),
                    )
                )
                if (omittedHook != SubscriptionHookSpecs.UpdatePlayback.hook) {
                    add(
                        ExtensionHookDeclaration(
                            hook = SubscriptionHookSpecs.UpdatePlayback.hook,
                            schemaVersion = SubscriptionHookSpecs.UpdatePlayback.schemaVersion,
                            requiredCapabilities = setOf(
                                ExtensionCapabilityIds.Network,
                                ExtensionCapabilityIds.CredentialRead,
                                ExtensionCapabilityIds.PlaybackResolve,
                            ),
                        )
                    )
                }
                add(
                    ExtensionHookDeclaration(
                        hook = SubscriptionHookSpecs.ClosePlayback.hook,
                        schemaVersion = SubscriptionHookSpecs.ClosePlayback.schemaVersion,
                        requiredCapabilities = setOf(
                            ExtensionCapabilityIds.Network,
                            ExtensionCapabilityIds.CredentialRead,
                            ExtensionCapabilityIds.PlaybackResolve,
                        ),
                    )
                )
            },
            capabilities = REQUIRED_CAPABILITIES.mapTo(mutableSetOf()) { capability ->
                ExtensionCapabilityRequest(
                    capability = capability,
                    reason = "Exercise provider catalog persistence",
                )
            },
        )

        override val handlers: Collection<ExtensionHandler<*, *>> =
            buildList<ExtensionHandler<*, *>> {
                add(discoverHandler())
                add(validateHandler())
                add(refreshHandler())
                if (omittedHook != SubscriptionHookSpecs.Browse.hook) add(browseHandler())
                add(resolvePlaybackHandler())
                if (omittedHook != SubscriptionHookSpecs.UpdatePlayback.hook) {
                    add(updatePlaybackHandler())
                }
                add(closePlaybackHandler())
            }

        private fun discoverHandler() = object :
            ExtensionHandler<
                SubscriptionProviderDiscoverRequest,
                SubscriptionProviderDiscoverResult,
                > {
            override val spec = SubscriptionHookSpecs.Discover

            override suspend fun invoke(
                context: ExtensionCallContext,
                request: SubscriptionProviderDiscoverRequest,
            ) = HookResult.Success(
                SubscriptionProviderDiscoverResult(provider = PROVIDER_DESCRIPTOR)
            )
        }

        private fun validateHandler() = object :
            ExtensionHandler<
                SubscriptionProviderValidateRequest,
                SubscriptionProviderValidateResult,
                > {
            override val spec = SubscriptionHookSpecs.Validate

            override suspend fun invoke(
                context: ExtensionCallContext,
                request: SubscriptionProviderValidateRequest,
            ): HookResult<SubscriptionProviderValidateResult> {
                val passwordHandle =
                    request.credentialHandles[SubscriptionProviderSettingKeys.Password]
                        ?: return authenticationFailure()
                if (credentialVault.consume(passwordHandle) != PASSWORD) {
                    return authenticationFailure()
                }
                return HookResult.Success(
                    SubscriptionProviderValidateResult(
                        evidence = ProviderValidationEvidence.TrustedDirect(
                            account = ValidatedProviderAccount(
                                normalizedBaseUrl = BASE_URL,
                                detectedKind = PROVIDER_KIND,
                                serverId = SERVER_ID,
                                serverName = "Browse server",
                                serverVersion = "1",
                                userId = USER_ID,
                                username = "viewer",
                            ),
                            credential = credentialVault.stage(ACCESS_TOKEN),
                        )
                    )
                )
            }
        }

        private fun refreshHandler() = object :
            ExtensionHandler<
                SubscriptionContentRefreshRequest,
                SubscriptionContentRefreshResult,
                > {
            override val spec = SubscriptionHookSpecs.Refresh

            override suspend fun invoke(
                context: ExtensionCallContext,
                request: SubscriptionContentRefreshRequest,
            ) = HookResult.Success(LIVE_REFRESH)
        }

        private fun browseHandler() = object :
            ExtensionHandler<
                SubscriptionContentBrowseRequest,
                SubscriptionContentBrowseResult,
                > {
            override val spec = SubscriptionHookSpecs.Browse

            override suspend fun invoke(
                context: ExtensionCallContext,
                request: SubscriptionContentBrowseRequest,
            ): HookResult<SubscriptionContentBrowseResult> {
                browseRequests += request
                return HookResult.Success(
                    requireNotNull(browsePages[request.cursor]) {
                        "No test browse page for cursor ${request.cursor}"
                    }
                )
            }
        }

        private fun resolvePlaybackHandler() = object :
            ExtensionHandler<
                PlaybackSourceResolveRequest,
                PlaybackSourceResolveResult,
                > {
            override val spec = SubscriptionHookSpecs.ResolvePlayback

            override suspend fun invoke(
                context: ExtensionCallContext,
                request: PlaybackSourceResolveRequest,
            ): HookResult<PlaybackSourceResolveResult> {
                resolveRequests += request
                return HookResult.Success(
                    PlaybackSourceResolveResult(
                        url = "$BASE_URL/playback",
                        playMethod = PlaybackMethods.DirectPlay,
                    )
                )
            }
        }

        private fun updatePlaybackHandler() = object :
            ExtensionHandler<
                PlaybackSessionUpdateRequest,
                PlaybackSessionUpdateResult,
                > {
            override val spec = SubscriptionHookSpecs.UpdatePlayback

            override suspend fun invoke(
                context: ExtensionCallContext,
                request: PlaybackSessionUpdateRequest,
            ) = HookResult.Success(PlaybackSessionUpdateResult(accepted = true))
        }

        private fun closePlaybackHandler() = object :
            ExtensionHandler<
                PlaybackSessionCloseRequest,
                PlaybackSessionCloseResult,
                > {
            override val spec = SubscriptionHookSpecs.ClosePlayback

            override suspend fun invoke(
                context: ExtensionCallContext,
                request: PlaybackSessionCloseRequest,
            ) = HookResult.Success(PlaybackSessionCloseResult(closed = true))
        }

        private fun <T : ExtensionPayload> authenticationFailure(): HookResult<T> =
            HookResult.Failure(
                ExtensionError(
                    code = SubscriptionProviderErrorCodes.AuthenticationFailed,
                    message = "Test credentials were rejected",
                    recoverable = false,
                )
            )
    }

    private class TestCredentialVault : CredentialVault {
        private val transientSecrets = linkedMapOf<String, String>()
        private var transientSequence = 0
        private var persistentSequence = 0

        override fun encrypt(
            accountId: String,
            secret: String,
            credentialHandle: String?,
        ) = ProviderCredentialEntity(
            accountId = accountId,
            credentialHandle = credentialHandle
                ?: "persistent:${++persistentSequence}:$accountId",
            ciphertext = secret,
            nonce = "test-nonce",
            keyVersion = 1,
        )

        override fun decrypt(credential: ProviderCredentialEntity): String = credential.ciphertext

        override fun stage(secret: String): CredentialHandle {
            val handle = CredentialHandle("transient:${++transientSequence}")
            transientSecrets[handle.value] = secret
            return handle
        }

        override fun consume(handle: CredentialHandle): String? =
            transientSecrets.remove(handle.value)
    }

    private data object NoOpExtensionContributionScheduler : ExtensionContributionScheduler {
        override suspend fun enqueue(playlistUrl: String) = Unit

        override suspend fun cancel(playlistUrl: String) = Unit
    }

    private companion object {
        val EXTENSION_ID = ExtensionId("com.m3u.test.provider.browse")
        val PROVIDER_KIND = ProviderKind("browse-test")
        val REQUIRED_CAPABILITIES = setOf(
            ExtensionCapabilityIds.Network,
            ExtensionCapabilityIds.CredentialRead,
            ExtensionCapabilityIds.CredentialWrite,
            ExtensionCapabilityIds.SubscriptionRead,
            ExtensionCapabilityIds.PlaybackResolve,
        )
        const val BASE_URL = "https://provider.example.test"
        const val SERVER_ID = "browse-server"
        const val USER_ID = "browse-user"
        const val PASSWORD = "test-password"
        const val ACCESS_TOKEN = "test-access-token"
        const val LIVE_REMOTE_ID = "live-1"
        const val MOVIE_ID = "movie-1"
        const val SERIES_ID = "series-1"
        const val SECOND_PAGE_CURSOR = "page-2"
        const val CATALOG_RELATION_PREFIX = "content:"

        val PROVIDER_DESCRIPTOR = SubscriptionProviderDescriptor(
            providerId = EXTENSION_ID,
            displayName = "Browse provider",
            variants = listOf(
                SubscriptionProviderVariant(
                    kind = PROVIDER_KIND,
                    displayName = "Browse",
                )
            ),
            settingsSchema = ExtensionSettingSchema(
                version = 1,
                fields = listOf(
                    ExtensionSettingField(
                        key = SubscriptionProviderSettingKeys.BaseUrl,
                        label = "Server URL",
                        type = ExtensionSettingType.TEXT,
                        required = true,
                    ),
                    ExtensionSettingField(
                        key = SubscriptionProviderSettingKeys.Password,
                        label = "Password",
                        type = ExtensionSettingType.SECRET,
                        required = true,
                    ),
                ),
            ),
        )

        val LIVE_REFRESH = SubscriptionContentRefreshResult(
            source = SubscriptionSourceDescriptor(
                remoteId = SERVER_ID,
                providerKind = PROVIDER_KIND,
            ),
            channels = listOf(
                SubscriptionChannelDescriptor(
                    remoteId = LIVE_REMOTE_ID,
                    title = "Live",
                    category = "Live",
                    playbackReference = PlaybackReference(
                        providerId = EXTENSION_ID,
                        itemId = LIVE_REMOTE_ID,
                        sourceType = "live",
                    ),
                )
            ),
        )

        fun initialBrowsePages(): Map<String?, SubscriptionContentBrowseResult> = mapOf(
            null to SubscriptionContentBrowseResult(
                items = listOf(
                    contentItem(
                        id = MOVIE_ID,
                        mediaKind = ProviderMediaKinds.Movie,
                        title = "Movie",
                        playable = true,
                        browsable = false,
                        mediaSourceId = "movie-source-initial",
                        category = "Movies",
                        subtitle = "Original release",
                        overview = "Initial movie overview",
                        productionYear = 2024,
                    )
                ),
                nextCursor = SECOND_PAGE_CURSOR,
                total = 2,
            ),
            SECOND_PAGE_CURSOR to SubscriptionContentBrowseResult(
                items = listOf(
                    contentItem(
                        id = SERIES_ID,
                        mediaKind = ProviderMediaKinds.Series,
                        title = "Series",
                        playable = false,
                        browsable = true,
                        category = "Series",
                    )
                ),
                total = 2,
            ),
        )

        fun refreshedBrowsePages(): Map<String?, SubscriptionContentBrowseResult> = mapOf(
            null to SubscriptionContentBrowseResult(
                items = listOf(
                    contentItem(
                        id = MOVIE_ID,
                        mediaKind = ProviderMediaKinds.Movie,
                        title = "Movie refreshed",
                        playable = true,
                        browsable = false,
                        mediaSourceId = "movie-source-refreshed",
                        category = "Featured",
                        subtitle = "Remastered",
                        overview = "Refreshed movie overview",
                        productionYear = 2025,
                    )
                ),
                nextCursor = SECOND_PAGE_CURSOR,
                total = 2,
            ),
            SECOND_PAGE_CURSOR to SubscriptionContentBrowseResult(
                items = listOf(
                    contentItem(
                        id = SERIES_ID,
                        mediaKind = ProviderMediaKinds.Series,
                        title = "Series refreshed",
                        playable = false,
                        browsable = true,
                        category = "Featured",
                    )
                ),
                total = 2,
            ),
        )

        fun contentItem(
            id: String,
            mediaKind: ProviderMediaKind,
            title: String,
            playable: Boolean,
            browsable: Boolean,
            mediaSourceId: String? = null,
            category: String? = null,
            subtitle: String? = null,
            overview: String? = null,
            productionYear: Int? = null,
        ) = SubscriptionContentItemDescriptor(
            reference = PlaybackReference(
                providerId = EXTENSION_ID,
                itemId = id,
                mediaSourceId = mediaSourceId,
                sourceType = mediaKind.value,
            ),
            mediaKind = mediaKind,
            title = title,
            playable = playable,
            browsable = browsable,
            category = category,
            subtitle = subtitle,
            overview = overview,
            productionYear = productionYear,
        )

        fun assertRootBrowseRequests(
            requests: List<SubscriptionContentBrowseRequest>,
            expectedCursors: List<String?>,
        ) {
            assertEquals(expectedCursors, requests.map { request -> request.cursor })
            assertTrue(requests.all { request -> request.parentReference == null })
            assertTrue(
                requests.all { request ->
                    request.limit == SubscriptionContentBrowseRequest.MAX_LIMIT
                }
            )
        }
    }
}
