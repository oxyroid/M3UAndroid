package com.m3u.testing.extension.reference

import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionSettingType
import com.m3u.extension.api.HostHookSpecs
import com.m3u.extension.api.HookResult
import com.m3u.extension.api.security.BrokerErrorCodes
import com.m3u.extension.api.security.BrokerValue
import com.m3u.extension.api.security.BrokerValueEncoding
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.api.security.ResponseValueSource
import com.m3u.extension.api.subscription.PlaybackReference
import com.m3u.extension.api.subscription.PlaybackSessionCloseReason
import com.m3u.extension.api.subscription.PlaybackSessionCloseRequest
import com.m3u.extension.api.subscription.PlaybackSessionDescriptor
import com.m3u.extension.api.subscription.PlaybackSourceResolveRequest
import com.m3u.extension.api.subscription.ProviderAccountReference
import com.m3u.extension.api.subscription.ProviderCredential
import com.m3u.extension.api.subscription.ProviderAuthenticationContextKeys
import com.m3u.extension.api.subscription.SubscriptionContentRefreshRequest
import com.m3u.extension.api.subscription.SubscriptionHookSpecs
import com.m3u.extension.api.subscription.SubscriptionProviderSettingKeys
import com.m3u.extension.api.subscription.SubscriptionProviderValidateRequest
import com.m3u.extension.api.subscription.SubscriptionProviderValidateResult
import com.m3u.extension.api.subscription.SubscriptionRefreshReason
import com.m3u.extension.sdk.android.BrokerException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class ReferenceProviderContractTest {
    private val passwordHandle = CredentialHandle("staged-password")
    private val tokenHandle = CredentialHandle("captured-token")
    private val account = ProviderAccountReference(
        accountId = "reference-account",
        providerId = REFERENCE_EXTENSION_ID,
        providerKind = REFERENCE_PROVIDER_KIND,
        baseUrl = "http://127.0.0.1:8080",
        serverId = "reference-server-id",
        serverName = "M3U Reference Provider",
        serverVersion = "1.0.0",
        userId = "reference-user-id",
        username = "m3u",
    )
    private val reference = PlaybackReference(
        providerId = REFERENCE_EXTENSION_ID,
        itemId = "reference.news",
        sourceType = "live",
    )

    @Test
    fun `manifest declares typed provider hooks and provider form`() {
        val declaredHooks = REFERENCE_MANIFEST.hooks.mapTo(mutableSetOf()) { it.hook }
        assertTrue(
            declaredHooks.containsAll(
                setOf(
                    SubscriptionHookSpecs.Discover.hook,
                    SubscriptionHookSpecs.Validate.hook,
                    SubscriptionHookSpecs.Refresh.hook,
                    SubscriptionHookSpecs.ResolvePlayback.hook,
                    SubscriptionHookSpecs.ClosePlayback.hook,
                    HostHookSpecs.SettingsSchema.hook,
                    HostHookSpecs.SearchProvider.hook,
                    HostHookSpecs.MetadataEnrichment.hook,
                    HostHookSpecs.EpgRefresh.hook,
                    HostHookSpecs.BackgroundTask.hook,
                )
            )
        )
        assertEquals(
            listOf(
                SubscriptionProviderSettingKeys.BaseUrl,
                SubscriptionProviderSettingKeys.Username,
                SubscriptionProviderSettingKeys.Password,
            ),
            REFERENCE_PROVIDER_SETTINGS.fields.map { it.key },
        )
        assertEquals(
            ExtensionSettingType.SECRET,
            REFERENCE_PROVIDER_SETTINGS.fields.single {
                it.key == SubscriptionProviderSettingKeys.Password
            }.type,
        )
    }

    @Test
    fun `login request keeps password and authentication response opaque`() {
        val call = SubscriptionProviderValidateRequest(
            providerKind = REFERENCE_PROVIDER_KIND,
            settingValues = mapOf(
                SubscriptionProviderSettingKeys.BaseUrl to "http://127.0.0.1:8080/",
                SubscriptionProviderSettingKeys.Username to "m3u",
            ),
            credentialHandles = mapOf(
                SubscriptionProviderSettingKeys.Password to passwordHandle,
            ),
        ).referenceLoginCall()

        assertEquals("http://127.0.0.1:8080", call.baseUrl)
        assertEquals(
            BrokerValue.Literal("http://127.0.0.1:8080/reference-provider/login"),
            call.request.exchange.url,
        )
        val encodedSecret = assertIs<BrokerValue.Encoded>(call.request.exchange.body[3])
        assertEquals(BrokerValueEncoding.JsonString, encodedSecret.encoding)
        val secret = assertIs<BrokerValue.Secret>(encodedSecret.value)
        assertEquals(passwordHandle, secret.reference.handle)
        assertEquals(
            ResponseValueSource.JsonPointer("/accessToken"),
            call.request.primaryCredentialSource,
        )
        assertEquals(
            listOf(
                ProviderAuthenticationContextKeys.ServerId to
                    ResponseValueSource.JsonPointer("/server_id"),
                ProviderAuthenticationContextKeys.UserId to
                    ResponseValueSource.JsonPointer("/user_id"),
            ),
            call.request.opaqueContexts.map { it.key to it.source },
        )
    }

    @Test
    fun `refresh resolve close and stream headers use captured token handle`() {
        val refresh = SubscriptionContentRefreshRequest(
            account = account,
            credential = ProviderCredential(tokenHandle),
            reason = SubscriptionRefreshReason.Initial,
        ).referenceRefreshCall()
        val resolve = PlaybackSourceResolveRequest(
            account = account,
            credential = ProviderCredential(tokenHandle),
            reference = reference,
        ).referencePlaybackCall()
        val close = PlaybackSessionCloseRequest(
            account = account,
            credential = ProviderCredential(tokenHandle),
            reference = reference,
            session = PlaybackSessionDescriptor(
                playSessionId = "reference-play-session-reference.news",
                liveStreamId = "reference-live-stream-reference.news",
            ),
            reason = PlaybackSessionCloseReason.Stopped,
        ).referenceCloseCall()

        listOf(refresh, resolve, close).forEach { request ->
            val secret = assertIs<BrokerValue.Secret>(request.headers.getValue("X-Emby-Token"))
            assertEquals(tokenHandle, secret.reference.handle)
        }
        val playbackSecret = assertIs<BrokerValue.Secret>(
            referencePlaybackHeaders(tokenHandle)
                .getValue("X-Emby-Token")
                .parts
                .single()
        )
        assertEquals(tokenHandle, playbackSecret.reference.handle)
        assertTrue(
            REFERENCE_MANIFEST.capabilities.any {
                it.capability == ExtensionCapabilityIds.CredentialRead
            }
        )
    }

    @Test
    fun `typed broker failure maps to a provider failure result`() = runBlocking {
        val result = providerBrokerResult<SubscriptionProviderValidateResult>("validation") {
            throw BrokerException(
                code = BrokerErrorCodes.NetworkFailed,
                recoverable = true,
                message = "The broker network request failed",
            )
        }

        val failure = assertIs<HookResult.Failure>(result)
        assertEquals("provider.broker_network_failed", failure.error.code.value)
        assertTrue(failure.error.recoverable)
        assertEquals("network_failed", failure.error.details["broker_code"])
    }
}
