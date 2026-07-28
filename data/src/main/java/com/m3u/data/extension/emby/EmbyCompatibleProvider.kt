package com.m3u.data.extension.emby

import android.content.Context
import android.content.res.Configuration
import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionCapabilityRequest
import com.m3u.extension.api.ExtensionError
import com.m3u.extension.api.ExtensionErrorCode
import com.m3u.extension.api.ExtensionHookDeclaration
import com.m3u.extension.api.ExtensionHandler
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionSemanticVersion
import com.m3u.extension.api.ExtensionSettingField
import com.m3u.extension.api.ExtensionSettingSchema
import com.m3u.extension.api.ExtensionSettingType
import com.m3u.extension.api.ExtensionCallContext
import com.m3u.extension.api.HookResult
import com.m3u.extension.api.HookSpec
import com.m3u.extension.api.ExtensionEntrypoint
import com.m3u.extension.api.subscription.EmbyCompatibleProviderKinds
import com.m3u.extension.api.subscription.PlaybackSessionCloseRequest
import com.m3u.extension.api.subscription.PlaybackSessionCloseResult
import com.m3u.extension.api.subscription.PlaybackHeaderValue
import com.m3u.extension.api.subscription.PlaybackSessionDescriptor
import com.m3u.extension.api.subscription.PlaybackSourceResolveRequest
import com.m3u.extension.api.subscription.PlaybackSourceResolveResult
import com.m3u.extension.api.subscription.ProviderAccountReference
import com.m3u.extension.api.subscription.ProviderValidationEvidence
import com.m3u.extension.api.subscription.SubscriptionContentRefreshRequest
import com.m3u.extension.api.subscription.SubscriptionContentRefreshResult
import com.m3u.extension.api.subscription.SubscriptionProviderDescriptor
import com.m3u.extension.api.subscription.SubscriptionProviderDiscoverRequest
import com.m3u.extension.api.subscription.SubscriptionProviderDiscoverResult
import com.m3u.extension.api.subscription.SubscriptionProviderErrorCodes
import com.m3u.extension.api.subscription.SubscriptionProviderSettingKeys
import com.m3u.extension.api.subscription.SubscriptionProviderVariant
import com.m3u.extension.api.subscription.SubscriptionProviderValidateRequest
import com.m3u.extension.api.subscription.SubscriptionProviderValidateResult
import com.m3u.extension.api.subscription.SubscriptionHookSpecs
import com.m3u.extension.api.subscription.SubscriptionSourceDescriptor
import com.m3u.extension.api.subscription.ValidatedProviderAccount
import com.m3u.data.extension.security.CredentialResolver
import com.m3u.extension.runtime.InvocationPolicy
import com.m3u.i18n.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class EmbyCompatibleProvider @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val client: EmbyCompatibleClient,
    private val credentialResolver: CredentialResolver,
    private val invocationPolicy: InvocationPolicy = InvocationPolicy(),
    private val cleanupScheduler: EmbyPlaybackCleanupScheduler =
        EmbyPlaybackCleanupScheduler(),
) : ExtensionEntrypoint {
    override val manifest = ExtensionManifest(
        id = ID,
        displayName = MANIFEST_DISPLAY_NAME,
        extensionVersion = ExtensionSemanticVersion(1, 0, 0),
        apiRange = ExtensionApiRange(
            minimum = ExtensionApiVersions.Current,
            maximum = ExtensionApiVersions.Current,
        ),
        hooks = setOf(
            ExtensionHookDeclaration(
                hook = SubscriptionHookSpecs.Discover.hook,
                schemaVersion = SubscriptionHookSpecs.Discover.schemaVersion,
            ),
            ExtensionHookDeclaration(
                hook = SubscriptionHookSpecs.Validate.hook,
                schemaVersion = SubscriptionHookSpecs.Validate.schemaVersion,
                requiredCapabilities = setOf(
                    ExtensionCapabilityIds.Network,
                    ExtensionCapabilityIds.CredentialWrite,
                ),
            ),
            ExtensionHookDeclaration(
                hook = SubscriptionHookSpecs.Refresh.hook,
                schemaVersion = SubscriptionHookSpecs.Refresh.schemaVersion,
                requiredCapabilities = setOf(
                    ExtensionCapabilityIds.Network,
                    ExtensionCapabilityIds.CredentialRead,
                    ExtensionCapabilityIds.SubscriptionRead,
                ),
            ),
            ExtensionHookDeclaration(
                hook = SubscriptionHookSpecs.ResolvePlayback.hook,
                schemaVersion = SubscriptionHookSpecs.ResolvePlayback.schemaVersion,
                requiredCapabilities = setOf(
                    ExtensionCapabilityIds.Network,
                    ExtensionCapabilityIds.CredentialRead,
                    ExtensionCapabilityIds.PlaybackResolve,
                ),
            ),
            ExtensionHookDeclaration(
                hook = SubscriptionHookSpecs.ClosePlayback.hook,
                schemaVersion = SubscriptionHookSpecs.ClosePlayback.schemaVersion,
                requiredCapabilities = setOf(
                    ExtensionCapabilityIds.Network,
                    ExtensionCapabilityIds.CredentialRead,
                    ExtensionCapabilityIds.PlaybackResolve,
                ),
            ),
        ),
        capabilities = setOf(
            ExtensionCapabilityRequest(
                capability = ExtensionCapabilityIds.Network,
                reason = "Connect to the configured media server",
            ),
            ExtensionCapabilityRequest(
                capability = ExtensionCapabilityIds.CredentialRead,
                reason = "Authenticate refresh and playback requests",
            ),
            ExtensionCapabilityRequest(
                capability = ExtensionCapabilityIds.CredentialWrite,
                reason = "Return the access token created by account validation",
            ),
            ExtensionCapabilityRequest(
                capability = ExtensionCapabilityIds.SubscriptionRead,
                reason = "Refresh a configured subscription account",
            ),
            ExtensionCapabilityRequest(
                capability = ExtensionCapabilityIds.PlaybackResolve,
                reason = "Resolve and close dynamic playback sessions",
            ),
        ),
    )

    override val handlers: Collection<ExtensionHandler<*, *>> = listOf(
        boundHandler(SubscriptionHookSpecs.Discover, ::discover),
        boundHandler(SubscriptionHookSpecs.Validate, ::validate),
        boundHandler(SubscriptionHookSpecs.Refresh, ::refresh),
        boundHandler(SubscriptionHookSpecs.ResolvePlayback, ::resolvePlayback),
        boundHandler(SubscriptionHookSpecs.ClosePlayback, ::closePlayback),
    )

    private suspend fun discover(
        context: ExtensionCallContext,
        request: SubscriptionProviderDiscoverRequest,
    ): HookResult<SubscriptionProviderDiscoverResult> {
        return HookResult.Success(
            payload = SubscriptionProviderDiscoverResult(
                provider = descriptorForLocale(applicationContext, request.localeTag),
            )
        )
    }

    private suspend fun validate(
        context: ExtensionCallContext,
        request: SubscriptionProviderValidateRequest,
    ): HookResult<SubscriptionProviderValidateResult> {
        val baseUrl = request.settingValues[SubscriptionProviderSettingKeys.BaseUrl]
            ?: return failure(INVALID_PAYLOAD, "Server URL is required")
        val username = request.settingValues[SubscriptionProviderSettingKeys.Username]
            ?: return failure(INVALID_PAYLOAD, "Username is required")
        val password = request.credentialHandles[SubscriptionProviderSettingKeys.Password]
            ?: return failure(INVALID_PAYLOAD, "Password is required")
        return providerCall {
            val result = client.validate(
                baseUrl = baseUrl,
                requestedKind = request.providerKind,
                username = username,
                password = credentialResolver.resolve(password)
                    ?: throw CredentialUnavailableException(),
            )
            SubscriptionProviderValidateResult(
                evidence = ProviderValidationEvidence.TrustedDirect(
                    account = result.account,
                    credential = credentialResolver.stage(result.accessToken),
                ),
            )
        }
    }

    private suspend fun refresh(
        context: ExtensionCallContext,
        request: SubscriptionContentRefreshRequest,
    ): HookResult<SubscriptionContentRefreshResult> {
        return providerCall {
            val account = request.account.toValidatedAccount()
            val accessToken = credentialResolver.resolve(request.credential.handle)
                ?: throw CredentialUnavailableException()
            val result = client.refreshChannels(account, accessToken)
            SubscriptionContentRefreshResult(
                source = SubscriptionSourceDescriptor(
                    remoteId = request.account.serverId,
                    providerKind = request.account.providerKind,
                ),
                channels = result.channels,
            )
        }
    }

    private suspend fun resolvePlayback(
        context: ExtensionCallContext,
        request: PlaybackSourceResolveRequest,
    ): HookResult<PlaybackSourceResolveResult> {
        return providerCall {
            val account = request.account.toValidatedAccount()
            val accessToken = credentialResolver.resolve(request.credential.handle)
                ?: throw CredentialUnavailableException()
            val cleanupAdmission = cleanupScheduler.tryReserve()
                ?: throw EmbyProtocolException(
                    "Provider playback cleanup capacity is exhausted"
                )
            val source = try {
                client.resolvePlaybackWithCleanupAdmission(
                    account = account,
                    accessToken = accessToken,
                    reference = request.reference,
                    preferences = request.preferences,
                    cleanupAdmission = cleanupAdmission,
                )
            } catch (failure: Exception) {
                cleanupAdmission.release()
                throw failure
            }
            try {
                currentCoroutineContext().ensureActive()
                val result = PlaybackSourceResolveResult(
                    url = source.url,
                    headers = source.headers.mapValues { (_, value) ->
                        PlaybackHeaderValue.literal(value)
                    },
                    mediaSourceId = source.mediaSourceId,
                    session = source.session?.let { session ->
                        PlaybackSessionDescriptor(
                            playSessionId = session.playSessionId,
                            liveStreamId = session.liveStreamId,
                        )
                    },
                )
                val responseSize = WIRE_JSON
                    .encodeToString(PlaybackSourceResolveResult.serializer(), result)
                    .encodeToByteArray()
                    .size
                if (responseSize > invocationPolicy.maxPayloadBytes) {
                    throw EmbyProtocolException(
                        "Provider playback response exceeds the host limit"
                    )
                }
                currentCoroutineContext().ensureActive()
                cleanupAdmission.release()
                result
            } catch (failure: Exception) {
                source.session?.let { session ->
                    scheduleCloseAfterResolveFailure(
                        cleanupAdmission = cleanupAdmission,
                        account = account,
                        accessToken = accessToken,
                        itemId = request.reference.itemId,
                        mediaSourceId = source.mediaSourceId,
                        session = session,
                    )
                } ?: cleanupAdmission.release()
                throw failure
            }
        }
    }

    private suspend fun closePlayback(
        context: ExtensionCallContext,
        request: PlaybackSessionCloseRequest,
    ): HookResult<PlaybackSessionCloseResult> {
        return providerCall {
            PlaybackSessionCloseResult(
                closed = client.closePlayback(
                    account = request.account.toValidatedAccount(),
                    accessToken = credentialResolver.resolve(request.credential.handle)
                        ?: throw CredentialUnavailableException(),
                    itemId = request.reference.itemId,
                    mediaSourceId = request.reference.mediaSourceId,
                    session = EmbyPlaybackSession(
                        playSessionId = request.session.playSessionId,
                        liveStreamId = request.session.liveStreamId,
                    ),
                )
            )
        }
    }

    private fun scheduleCloseAfterResolveFailure(
        cleanupAdmission: EmbyPlaybackCleanupAdmission,
        account: ValidatedProviderAccount,
        accessToken: String,
        itemId: String,
        mediaSourceId: String?,
        session: EmbyPlaybackSession,
    ) {
        cleanupAdmission.schedule {
            client.closePlayback(
                account = account,
                accessToken = accessToken,
                itemId = itemId,
                mediaSourceId = mediaSourceId,
                session = session,
            )
        }
    }

    private suspend fun <Response : com.m3u.extension.api.ExtensionPayload> providerCall(
        block: suspend () -> Response,
    ): HookResult<Response> =
        try {
            HookResult.Success(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: EmbyHttpException) {
            failure(
                code = if (exception.statusCode == 401 || exception.statusCode == 403) {
                    SubscriptionProviderErrorCodes.AuthenticationFailed
                } else {
                    PROVIDER_REQUEST_FAILED
                },
                message = exception.message ?: "Provider request failed",
                recoverable = exception.statusCode >= 500,
                details = mapOf("statusCode" to exception.statusCode.toString()),
            )
        } catch (exception: CredentialUnavailableException) {
            failure(
                code = SubscriptionProviderErrorCodes.AuthenticationFailed,
                message = exception.message.orEmpty(),
            )
        } catch (exception: Exception) {
            failure(
                code = PROVIDER_REQUEST_FAILED,
                message = exception.message ?: "Provider request failed",
                recoverable = true,
                details = mapOf("exception" to exception.javaClass.simpleName),
            )
        }

    private fun failure(
        code: ExtensionErrorCode,
        message: String,
        recoverable: Boolean = false,
        details: Map<String, String> = emptyMap(),
    ): HookResult.Failure = HookResult.Failure(
        ExtensionError(
            code = code,
            message = message,
            recoverable = recoverable,
            details = details,
        )
    )

    private fun <Request : com.m3u.extension.api.ExtensionPayload, Response : com.m3u.extension.api.ExtensionPayload> boundHandler(
        spec: HookSpec<Request, Response>,
        block: suspend (ExtensionCallContext, Request) -> HookResult<Response>,
    ): ExtensionHandler<Request, Response> = object : ExtensionHandler<Request, Response> {
        override val spec: HookSpec<Request, Response> = spec

        override suspend fun invoke(
            context: ExtensionCallContext,
            request: Request,
        ): HookResult<Response> = block(context, request)
    }

    private fun ProviderAccountReference.toValidatedAccount(): ValidatedProviderAccount = ValidatedProviderAccount(
            normalizedBaseUrl = baseUrl,
            detectedKind = providerKind,
            serverId = serverId,
            serverName = serverName,
            serverVersion = serverVersion,
            userId = userId,
            username = username,
        )

    companion object {
        val ID = ExtensionId("com.m3u.provider.emby-compatible")

        internal fun descriptorForLocale(
            context: Context,
            localeTag: String?,
        ): SubscriptionProviderDescriptor {
            val labels = context.providerLabelsForLocale(localeTag)
            return SubscriptionProviderDescriptor(
                providerId = ID,
                displayName = "Emby / Jellyfin",
                variants = listOf(
                    SubscriptionProviderVariant(
                        kind = EmbyCompatibleProviderKinds.Emby,
                        displayName = "Emby",
                    ),
                    SubscriptionProviderVariant(
                        kind = EmbyCompatibleProviderKinds.Jellyfin,
                        displayName = "Jellyfin",
                    ),
                    SubscriptionProviderVariant(
                        kind = EmbyCompatibleProviderKinds.Auto,
                        displayName = labels.automatic,
                        userSelectable = false,
                    ),
                ),
                settingsSchema = ExtensionSettingSchema(
                    version = 1,
                    fields = listOf(
                        ExtensionSettingField(
                            key = SubscriptionProviderSettingKeys.BaseUrl,
                            label = labels.serverUrl,
                            type = ExtensionSettingType.TEXT,
                            required = true,
                        ),
                        ExtensionSettingField(
                            key = SubscriptionProviderSettingKeys.Username,
                            label = labels.username,
                            type = ExtensionSettingType.TEXT,
                            required = true,
                        ),
                        ExtensionSettingField(
                            key = SubscriptionProviderSettingKeys.Password,
                            label = labels.password,
                            type = ExtensionSettingType.SECRET,
                            required = true,
                        ),
                    ),
                ),
            )
        }

        private fun Context.providerLabelsForLocale(localeTag: String?): ProviderLabels {
            val localizedContext = localeTag
                ?.trim()
                ?.replace('_', '-')
                ?.takeIf { tag -> tag.isNotBlank() }
                ?.let { tag ->
                    val configuration = Configuration(resources.configuration).apply {
                        setLocale(Locale.forLanguageTag(tag))
                    }
                    createConfigurationContext(configuration)
                }
                ?: this
            return ProviderLabels(
                serverUrl = localizedContext.getString(
                    R.string.feat_setting_placeholder_basic_url
                ).let(::wireSafeDisplayText),
                username = localizedContext.getString(
                    R.string.feat_setting_placeholder_username
                ).let(::wireSafeDisplayText),
                password = localizedContext.getString(
                    R.string.feat_setting_placeholder_password
                ).let(::wireSafeDisplayText),
                automatic = localizedContext.getString(
                    R.string.feat_setting_provider_variant_automatic
                ).let(::wireSafeDisplayText),
            )
        }

        internal fun wireSafeDisplayText(value: String): String = buildString(value.length) {
            value.forEach { character ->
                when {
                    character.isExtensionBidiControl() -> Unit
                    character.isISOControl() -> {
                        if (isNotEmpty() && last() != ' ') append(' ')
                    }
                    else -> append(character)
                }
            }
        }.trim()

        private fun Char.isExtensionBidiControl(): Boolean =
            code == 0x061C ||
                code in 0x202A..0x202E ||
                code in 0x2066..0x2069 ||
                code == 0x200E ||
                code == 0x200F

        private data class ProviderLabels(
            val serverUrl: String,
            val username: String,
            val password: String,
            val automatic: String,
        )

        private const val MANIFEST_DISPLAY_NAME = "Emby Compatible"
        private val INVALID_PAYLOAD = ExtensionErrorCode("provider.invalid_payload")
        private val PROVIDER_REQUEST_FAILED = ExtensionErrorCode("provider.request_failed")
        private val WIRE_JSON = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}

private class CredentialUnavailableException :
    IllegalStateException("Provider credential must be entered again")
