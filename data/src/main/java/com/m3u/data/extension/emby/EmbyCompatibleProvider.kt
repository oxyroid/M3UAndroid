package com.m3u.data.extension.emby

import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionCapabilityRequest
import com.m3u.extension.api.ExtensionEntrypoint
import com.m3u.extension.api.ExtensionError
import com.m3u.extension.api.ExtensionErrorCode
import com.m3u.extension.api.ExtensionHook
import com.m3u.extension.api.ExtensionHookDeclaration
import com.m3u.extension.api.ExtensionHookIds
import com.m3u.extension.api.ExtensionHookOutcome
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionInvocation
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionPayload
import com.m3u.extension.api.Hook
import com.m3u.extension.api.subscription.EmbyCompatibleProviderKinds
import com.m3u.extension.api.subscription.PlaybackSessionCloseRequest
import com.m3u.extension.api.subscription.PlaybackSessionCloseResult
import com.m3u.extension.api.subscription.PlaybackSourceResolveRequest
import com.m3u.extension.api.subscription.PlaybackSourceResolveResult
import com.m3u.extension.api.subscription.ProviderAccountReference
import com.m3u.extension.api.subscription.ProviderAuthentication
import com.m3u.extension.api.subscription.SubscriptionContentRefreshRequest
import com.m3u.extension.api.subscription.SubscriptionContentRefreshResult
import com.m3u.extension.api.subscription.SubscriptionProviderDescriptor
import com.m3u.extension.api.subscription.SubscriptionProviderDiscoverRequest
import com.m3u.extension.api.subscription.SubscriptionProviderDiscoverResult
import com.m3u.extension.api.subscription.SubscriptionProviderValidateRequest
import com.m3u.extension.api.subscription.SubscriptionProviderValidateResult
import com.m3u.extension.api.subscription.SubscriptionSourceDescriptor
import com.m3u.extension.api.subscription.ValidatedProviderAccount
import java.util.concurrent.CancellationException
import javax.inject.Inject

internal class EmbyCompatibleProvider @Inject constructor(
    private val client: EmbyCompatibleClient,
) : ExtensionEntrypoint {
    override val manifest = ExtensionManifest(
        id = ID,
        displayName = DISPLAY_NAME,
        extensionVersion = "1.0.0",
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

    override val hooks: Collection<ExtensionHook> = listOf(
        boundHook(ExtensionHookIds.SubscriptionProviderDiscover, ::discover),
        boundHook(ExtensionHookIds.SubscriptionProviderValidate, ::validate),
        boundHook(ExtensionHookIds.SubscriptionContentRefresh, ::refresh),
        boundHook(ExtensionHookIds.PlaybackSourceResolve, ::resolvePlayback),
        boundHook(ExtensionHookIds.PlaybackSessionClose, ::closePlayback),
    )

    private suspend fun discover(invocation: ExtensionInvocation): ExtensionHookOutcome {
        if (invocation.payload !is SubscriptionProviderDiscoverRequest) return invalidPayload(invocation)
        return ExtensionHookOutcome.Success(
            SubscriptionProviderDiscoverResult(
                providers = listOf(
                    SubscriptionProviderDescriptor(
                        providerId = ID,
                        displayName = DISPLAY_NAME,
                        supportedKinds = setOf(
                            EmbyCompatibleProviderKinds.Emby,
                            EmbyCompatibleProviderKinds.Jellyfin,
                            EmbyCompatibleProviderKinds.Auto,
                        ),
                    )
                )
            )
        )
    }

    private suspend fun validate(invocation: ExtensionInvocation): ExtensionHookOutcome {
        val request = invocation.payload as? SubscriptionProviderValidateRequest
            ?: return invalidPayload(invocation)
        val authentication = request.authentication as? ProviderAuthentication.UsernamePassword
            ?: return failure(
                code = INVALID_PAYLOAD,
                message = "This provider requires username and password authentication",
            )
        return providerCall {
            val result = client.validate(
                baseUrl = request.baseUrl,
                requestedKind = request.providerKind,
                username = authentication.username,
                password = authentication.password,
            )
            SubscriptionProviderValidateResult(
                account = result.account,
                accessToken = result.accessToken,
            )
        }
    }

    private suspend fun refresh(invocation: ExtensionInvocation): ExtensionHookOutcome {
        val request = invocation.payload as? SubscriptionContentRefreshRequest
            ?: return invalidPayload(invocation)
        return providerCall {
            val account = request.account.toValidatedAccount()
            val result = client.refreshChannels(account, request.credential.accessToken)
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

    private suspend fun resolvePlayback(invocation: ExtensionInvocation): ExtensionHookOutcome {
        val request = invocation.payload as? PlaybackSourceResolveRequest
            ?: return invalidPayload(invocation)
        return providerCall {
            val source = client.resolvePlayback(
                account = request.account.toValidatedAccount(),
                accessToken = request.credential.accessToken,
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

    private suspend fun closePlayback(invocation: ExtensionInvocation): ExtensionHookOutcome {
        val request = invocation.payload as? PlaybackSessionCloseRequest
            ?: return invalidPayload(invocation)
        return providerCall {
            PlaybackSessionCloseResult(
                closed = client.closePlayback(
                    account = request.account.toValidatedAccount(),
                    accessToken = request.credential.accessToken,
                    reference = request.reference,
                    session = request.session,
                )
            )
        }
    }

    private suspend fun providerCall(block: suspend () -> ExtensionPayload): ExtensionHookOutcome =
        try {
            ExtensionHookOutcome.Success(block())
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

    private fun invalidPayload(invocation: ExtensionInvocation): ExtensionHookOutcome = failure(
        code = INVALID_PAYLOAD,
        message = "Unexpected payload for hook ${invocation.hook}",
    )

    private fun failure(
        code: ExtensionErrorCode,
        message: String,
        recoverable: Boolean = false,
        details: Map<String, String> = emptyMap(),
    ): ExtensionHookOutcome.Failure = ExtensionHookOutcome.Failure(
        ExtensionError(
            code = code,
            message = message,
            recoverable = recoverable,
            details = details,
        )
    )

    private fun boundHook(
        hook: Hook,
        block: suspend (ExtensionInvocation) -> ExtensionHookOutcome,
    ): ExtensionHook = object : ExtensionHook {
        override val hook: Hook = hook

        override suspend fun invoke(invocation: ExtensionInvocation): ExtensionHookOutcome = block(invocation)
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
        private const val DISPLAY_NAME = "Emby Compatible"
        private val INVALID_PAYLOAD = ExtensionErrorCode("provider.invalid_payload")
        private val AUTHENTICATION_FAILED = ExtensionErrorCode("provider.authentication_failed")
        private val PROVIDER_REQUEST_FAILED = ExtensionErrorCode("provider.request_failed")
    }
}
