package com.m3u.testing.extension.reference

import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionSettingType
import com.m3u.extension.api.HostHookSpecs
import com.m3u.extension.api.HookResult
import com.m3u.extension.api.SearchProviderRequest
import com.m3u.extension.api.security.BrokerErrorCodes
import com.m3u.extension.api.security.BrokeredHttpRequest
import com.m3u.extension.api.security.BrokeredHttpResponse
import com.m3u.extension.api.security.BrokerValue
import com.m3u.extension.api.security.BrokerValueEncoding
import com.m3u.extension.api.security.ContextReference
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.api.security.ResponseValueSource
import com.m3u.extension.api.security.SecretReference
import com.m3u.extension.api.subscription.PlaybackReference
import com.m3u.extension.api.subscription.PlaybackSessionCloseReason
import com.m3u.extension.api.subscription.PlaybackSessionCloseRequest
import com.m3u.extension.api.subscription.PlaybackSessionCloseResult
import com.m3u.extension.api.subscription.PlaybackSourceResolveRequest
import com.m3u.extension.api.subscription.PlaybackSourceResolveResult
import com.m3u.extension.api.subscription.ProviderAccountReference
import com.m3u.extension.api.subscription.ProviderAuthenticationContextKeys
import com.m3u.extension.api.subscription.ProviderCredential
import com.m3u.extension.api.subscription.SubscriptionContentRefreshRequest
import com.m3u.extension.api.subscription.SubscriptionContentRefreshResult
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ReferenceProviderContractTest {
    private val passwordHandle = CredentialHandle("staged-password")

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
        assertEquals(2, HostHookSpecs.BackgroundTask.schemaVersion)
        assertEquals(
            listOf("settings-status"),
            REFERENCE_MANIFEST.backgroundTasks.map { declaration -> declaration.taskId },
        )
        assertEquals(
            setOf("https://reference.invalid:443"),
            REFERENCE_MANIFEST.networkOrigins.mapTo(mutableSetOf()) { it.canonicalValue },
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
    fun `protected data hooks declare credential broker access`() {
        val declarations = REFERENCE_MANIFEST.hooks.associateBy { declaration ->
            declaration.hook
        }
        listOf(
            SubscriptionHookSpecs.Refresh,
            SubscriptionHookSpecs.ResolvePlayback,
            SubscriptionHookSpecs.ClosePlayback,
        ).forEach { spec ->
            val required = declarations.getValue(spec.hook).requiredCapabilities
            assertTrue(ExtensionCapabilityIds.Network in required)
            assertTrue(ExtensionCapabilityIds.CredentialRead in required)
        }
        assertTrue(
            ExtensionCapabilityIds.SubscriptionRead in declarations
                .getValue(SubscriptionHookSpecs.Refresh.hook)
                .requiredCapabilities
        )
        listOf(
            SubscriptionHookSpecs.ResolvePlayback,
            SubscriptionHookSpecs.ClosePlayback,
        ).forEach { spec ->
            assertTrue(
                ExtensionCapabilityIds.PlaybackResolve in declarations
                    .getValue(spec.hook)
                    .requiredCapabilities
            )
        }
        val validateRequired = declarations
            .getValue(SubscriptionHookSpecs.Validate.hook)
            .requiredCapabilities
        assertTrue(ExtensionCapabilityIds.Network in validateRequired)
        assertTrue(ExtensionCapabilityIds.CredentialRead in validateRequired)
        assertTrue(ExtensionCapabilityIds.CredentialWrite in validateRequired)
        assertTrue(
            REFERENCE_MANIFEST.capabilities.any {
                it.capability == ExtensionCapabilityIds.CredentialRead
            }
        )
        val searchRequired = declarations
            .getValue(HostHookSpecs.SearchProvider.hook)
            .requiredCapabilities
        assertTrue(ExtensionCapabilityIds.Network in searchRequired)
        assertTrue(ExtensionCapabilityIds.CredentialRead in searchRequired)
    }

    @Test
    fun `search request is bound to one selected account and opaque credential`() {
        val request = SearchProviderRequest(
            query = "news",
            account = ACCOUNT,
            credential = CREDENTIAL,
        ).referenceSearchRequest()

        assertEquals(
            BrokerValue.Literal("$BASE_URL/reference-provider/channels"),
            request.url,
        )
        assertProtectedHeaders(request)
    }

    @Test
    fun `refresh request uses saved credential and context and parses a complete snapshot`() {
        val request = referenceRefreshRequest()
        val brokerRequest = request.referenceRefreshRequest()

        assertEquals("GET", brokerRequest.method)
        assertEquals(
            BrokerValue.Literal("$BASE_URL/reference-provider/channels"),
            brokerRequest.url,
        )
        assertProtectedHeaders(brokerRequest)

        val result = BrokeredHttpResponse(
            statusCode = 200,
            headers = emptyMap(),
            body =
                """{"source_id":"raw-server-id","source_title":"Reference Live TV","revision":"1","channels":[{"id":"reference.news","title":"Reference News","category":"News","epg_reference":"reference.news"},{"id":"reference.sports","title":"Reference Sports","category":"Sports","epg_reference":"reference.sports"}]}""",
        ).referenceRefreshResult(request)
        val snapshot = assertIs<SubscriptionContentRefreshResult>(
            assertIs<HookResult.Success<*>>(result).payload
        )

        assertEquals(ACCOUNT.serverId, snapshot.source.remoteId)
        assertEquals(REFERENCE_PROVIDER_KIND, snapshot.source.providerKind)
        assertEquals(
            listOf("reference.news", "reference.sports"),
            snapshot.channels.map { channel -> channel.remoteId },
        )
        assertTrue(
            snapshot.channels.all { channel ->
                channel.playbackReference.providerId == REFERENCE_EXTENSION_ID &&
                    channel.playbackReference.sourceType == "live"
            }
        )
    }

    @Test
    fun `playback and close requests keep protected values opaque`() {
        val resolveRequest = PlaybackSourceResolveRequest(
            account = ACCOUNT,
            credential = CREDENTIAL,
            reference = PLAYBACK_REFERENCE,
        )
        val playbackRequest = resolveRequest.referencePlaybackRequest()

        assertEquals("GET", playbackRequest.method)
        assertEquals(
            BrokerValue.Literal("$BASE_URL/reference-provider/playback/reference.news"),
            playbackRequest.url,
        )
        assertProtectedHeaders(playbackRequest)
        val playbackResult = BrokeredHttpResponse(
            statusCode = 200,
            headers = emptyMap(),
            body =
                """{"url":"$BASE_URL/reference-provider/stream/reference.news/index.m3u8","media_source_id":"reference-media-reference.news","play_session_id":"reference-play-session-reference.news","live_stream_id":"reference-live-stream-reference.news"}""",
        ).referencePlaybackResult(resolveRequest)
        val playback = assertIs<HookResult.Success<*>>(playbackResult).payload
        val source = assertIs<PlaybackSourceResolveResult>(playback)
        assertEquals(
            BrokerValue.Secret(SecretReference(CREDENTIAL_HANDLE)),
            source.headers.getValue("X-Emby-Token").parts.single(),
        )
        assertEquals(
            BrokerValue.Context(ContextReference(ProviderAuthenticationContextKeys.UserId)),
            source.headers.getValue("X-Reference-User").parts.single(),
        )
        val session = requireNotNull(source.session)

        val closeRequest = PlaybackSessionCloseRequest(
            account = ACCOUNT,
            credential = CREDENTIAL,
            reference = PLAYBACK_REFERENCE,
            session = session,
            reason = PlaybackSessionCloseReason.Stopped,
        ).referenceCloseRequest()
        assertEquals("POST", closeRequest.method)
        assertEquals(
            BrokerValue.Literal("$BASE_URL/reference-provider/sessions/close"),
            closeRequest.url,
        )
        assertProtectedHeaders(closeRequest)
        val closeBody = Json.parseToJsonElement(
            assertIs<BrokerValue.Literal>(closeRequest.body.single()).value
        ).jsonObject
        assertEquals(
            setOf("item_id", "play_session_id", "live_stream_id", "reason"),
            closeBody.keys,
        )
        assertEquals("reference.news", closeBody.getValue("item_id").jsonPrimitive.content)
        assertEquals("stopped", closeBody.getValue("reason").jsonPrimitive.content)
        val closeResult = BrokeredHttpResponse(
            statusCode = 200,
            headers = emptyMap(),
            body = """{"closed":true}""",
        ).referenceCloseResult()
        assertTrue(
            assertIs<PlaybackSessionCloseResult>(
                assertIs<HookResult.Success<*>>(closeResult).payload
            ).closed
        )
    }

    @Test
    fun `provider responses reject unknown fields and map HTTP authentication errors`() {
        val request = referenceRefreshRequest()
        val malformed = BrokeredHttpResponse(
            statusCode = 200,
            headers = emptyMap(),
            body =
                """{"source_id":"raw-server-id","source_title":"Reference Live TV","revision":"1","channels":[],"unexpected":true}""",
        ).referenceRefreshResult(request)
        assertEquals(
            "provider.invalid_response",
            assertIs<HookResult.Failure>(malformed).error.code.value,
        )

        val unauthorized = BrokeredHttpResponse(
            statusCode = 401,
            headers = emptyMap(),
            body = "credential rejected",
        ).referenceRefreshResult(request)
        assertEquals(
            "provider.authentication_failed",
            assertIs<HookResult.Failure>(unauthorized).error.code.value,
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

    private fun referenceRefreshRequest() = SubscriptionContentRefreshRequest(
        account = ACCOUNT,
        credential = CREDENTIAL,
        reason = SubscriptionRefreshReason.Manual,
    )

    private fun assertProtectedHeaders(request: BrokeredHttpRequest) {
        assertEquals(
            BrokerValue.Secret(SecretReference(CREDENTIAL_HANDLE)),
            request.headers["X-Emby-Token"],
        )
        assertEquals(
            BrokerValue.Context(ContextReference(ProviderAuthenticationContextKeys.UserId)),
            request.headers["X-Reference-User"],
        )
    }

    private companion object {
        const val BASE_URL = "http://127.0.0.1:8080"
        val CREDENTIAL_HANDLE = CredentialHandle("saved-access-token")
        val CREDENTIAL = ProviderCredential(CREDENTIAL_HANDLE)
        val ACCOUNT = ProviderAccountReference(
            accountId = "reference-account",
            providerId = REFERENCE_EXTENSION_ID,
            providerKind = REFERENCE_PROVIDER_KIND,
            baseUrl = BASE_URL,
            serverId = "stable-server-id",
            serverName = "Reference Provider",
            serverVersion = "",
            userId = "stable-user-id",
            username = "m3u",
        )
        val PLAYBACK_REFERENCE = PlaybackReference(
            providerId = REFERENCE_EXTENSION_ID,
            itemId = "reference.news",
            sourceType = "live",
        )
    }
}
