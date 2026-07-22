package com.m3u.data.extension.emby

import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionCapabilityRequest
import com.m3u.extension.api.ExtensionError
import com.m3u.extension.api.ExtensionErrorCode
import com.m3u.extension.api.ExtensionHookDeclaration
import com.m3u.extension.api.ExtensionHookIds
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
import com.m3u.extension.api.subscription.PlaybackSourceResolveRequest
import com.m3u.extension.api.subscription.PlaybackSourceResolveResult
import com.m3u.extension.api.subscription.ProviderAccountReference
import com.m3u.extension.api.subscription.SubscriptionContentRefreshRequest
import com.m3u.extension.api.subscription.SubscriptionContentRefreshResult
import com.m3u.extension.api.subscription.SubscriptionProviderDescriptor
import com.m3u.extension.api.subscription.SubscriptionProviderDiscoverRequest
import com.m3u.extension.api.subscription.SubscriptionProviderDiscoverResult
import com.m3u.extension.api.subscription.SubscriptionProviderSettingKeys
import com.m3u.extension.api.subscription.SubscriptionProviderValidateRequest
import com.m3u.extension.api.subscription.SubscriptionProviderValidateResult
import com.m3u.extension.api.subscription.SubscriptionHookSpecs
import com.m3u.extension.api.subscription.SubscriptionSourceDescriptor
import com.m3u.extension.api.subscription.ValidatedProviderAccount
import com.m3u.data.extension.security.CredentialResolver
import java.util.concurrent.CancellationException
import javax.inject.Inject

internal class EmbyCompatibleProvider @Inject constructor(
    private val client: EmbyCompatibleClient,
    private val credentialResolver: CredentialResolver,
) : ExtensionEntrypoint {
    override val manifest = ExtensionManifest(
        id = ID,
        displayName = DISPLAY_NAME,
        extensionVersion = ExtensionSemanticVersion(1, 0, 0),
        apiRange = ExtensionApiRange(
            minimum = ExtensionApiVersions.Current,
            maximum = ExtensionApiVersions.Current,
        ),
        hooks = setOf(
            ExtensionHookDeclaration(ExtensionHookIds.SubscriptionProviderDiscover),
            ExtensionHookDeclaration(
                hook = ExtensionHookIds.SubscriptionProviderValidate,
                requiredCapabilities = setOf(
                    ExtensionCapabilityIds.Network,
                    ExtensionCapabilityIds.CredentialWrite,
                ),
            ),
            ExtensionHookDeclaration(
                hook = ExtensionHookIds.SubscriptionContentRefresh,
                requiredCapabilities = setOf(
                    ExtensionCapabilityIds.Network,
                    ExtensionCapabilityIds.CredentialRead,
                    ExtensionCapabilityIds.SubscriptionRead,
                ),
            ),
            ExtensionHookDeclaration(
                hook = ExtensionHookIds.PlaybackSourceResolve,
                requiredCapabilities = setOf(
                    ExtensionCapabilityIds.Network,
                    ExtensionCapabilityIds.CredentialRead,
                    ExtensionCapabilityIds.PlaybackResolve,
                ),
            ),
            ExtensionHookDeclaration(
                hook = ExtensionHookIds.PlaybackSessionClose,
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
                providers = listOf(
                    SubscriptionProviderDescriptor(
                        providerId = ID,
                        displayName = DISPLAY_NAME,
                        supportedKinds = setOf(
                            EmbyCompatibleProviderKinds.Emby,
                            EmbyCompatibleProviderKinds.Jellyfin,
                            EmbyCompatibleProviderKinds.Auto,
                        ),
                        settingsSchema = SETTINGS_SCHEMA,
                    )
                )
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
                    ?: error("Provider credential must be entered again"),
            )
            SubscriptionProviderValidateResult(
                account = result.account,
                credential = credentialResolver.stage(result.accessToken),
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
                ?: error("Provider credential must be entered again")
            val result = client.refreshChannels(account, accessToken)
            SubscriptionContentRefreshResult(
                source = SubscriptionSourceDescriptor(
                    remoteId = request.account.serverId,
                    title = account.serverName,
                    providerKind = request.account.providerKind,
                ),
                channels = result.channels,
                syncMetadata = mapOf("totalRecordCount" to result.totalRecordCount.toString()),
            )
        }
    }

    private suspend fun resolvePlayback(
        context: ExtensionCallContext,
        request: PlaybackSourceResolveRequest,
    ): HookResult<PlaybackSourceResolveResult> {
        return providerCall {
            val source = client.resolvePlayback(
                account = request.account.toValidatedAccount(),
                accessToken = credentialResolver.resolve(request.credential.handle)
                    ?: error("Provider credential must be entered again"),
                reference = request.reference,
                preferences = request.preferences,
            )
            PlaybackSourceResolveResult(
                url = source.url,
                headers = source.headers,
                mediaSourceId = source.mediaSourceId,
                session = source.session,
            )
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
                        ?: error("Provider credential must be entered again"),
                    reference = request.reference,
                    session = request.session,
                )
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
                    AUTHENTICATION_FAILED
                } else {
                    PROVIDER_REQUEST_FAILED
                },
                message = exception.message ?: "Provider request failed",
                recoverable = exception.statusCode >= 500,
                details = mapOf("statusCode" to exception.statusCode.toString()),
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
        val SETTINGS_SCHEMA = ExtensionSettingSchema(
            version = 1,
            fields = listOf(
                ExtensionSettingField(
                    key = SubscriptionProviderSettingKeys.BaseUrl,
                    label = "Server URL",
                    type = ExtensionSettingType.TEXT,
                    required = true,
                ),
                ExtensionSettingField(
                    key = SubscriptionProviderSettingKeys.Username,
                    label = "Username",
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
        )
        private const val DISPLAY_NAME = "Emby Compatible"
        private val INVALID_PAYLOAD = ExtensionErrorCode("provider.invalid_payload")
        private val AUTHENTICATION_FAILED = ExtensionErrorCode("provider.authentication_failed")
        private val PROVIDER_REQUEST_FAILED = ExtensionErrorCode("provider.request_failed")
    }
}
