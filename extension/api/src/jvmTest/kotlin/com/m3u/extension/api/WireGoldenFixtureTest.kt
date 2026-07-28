package com.m3u.extension.api

import com.m3u.extension.api.security.BrokerInvocation
import com.m3u.extension.api.security.BrokerInvocationResult
import com.m3u.extension.api.security.BrokerOperation
import com.m3u.extension.api.security.BrokerOperationResult
import com.m3u.extension.api.security.BrokerValue
import com.m3u.extension.api.security.BrokeredHttpRequest
import com.m3u.extension.api.security.ResponseValueSource
import com.m3u.extension.api.subscription.ProviderValidationEvidence
import com.m3u.extension.api.subscription.SubscriptionHookSpecs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

class WireGoldenFixtureTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /*
     * Keep every supported schema version in this explicit catalog. When a Hook gains a schema,
     * append its fixtures without removing the older entry.
     */
    private val hookFixtureCatalog = listOf(
        hookFixture(
            hook = SubscriptionHookSpecs.Discover.hook,
            schemaVersion = 4,
            directory = "hooks/subscription.provider.discover/schema-4",
            requestSerializer = SubscriptionHookSpecs.Discover.requestSerializer,
            resultSerializer = SubscriptionHookSpecs.Discover.responseSerializer,
        ),
        hookFixture(
            hook = SubscriptionHookSpecs.Validate.hook,
            schemaVersion = 2,
            directory = "hooks/subscription.provider.validate/schema-2",
            requestSerializer = SubscriptionHookSpecs.Validate.requestSerializer,
            resultSerializer = SubscriptionHookSpecs.Validate.responseSerializer,
        ),
        hookFixture(
            hook = SubscriptionHookSpecs.Refresh.hook,
            schemaVersion = 4,
            directory = "hooks/subscription.content.refresh/schema-4",
            requestSerializer = SubscriptionHookSpecs.Refresh.requestSerializer,
            resultSerializer = SubscriptionHookSpecs.Refresh.responseSerializer,
        ),
        hookFixture(
            hook = SubscriptionHookSpecs.ResolvePlayback.hook,
            schemaVersion = 4,
            directory = "hooks/playback.source.resolve/schema-4",
            requestSerializer = SubscriptionHookSpecs.ResolvePlayback.requestSerializer,
            resultSerializer = SubscriptionHookSpecs.ResolvePlayback.responseSerializer,
        ),
        hookFixture(
            hook = SubscriptionHookSpecs.ClosePlayback.hook,
            schemaVersion = 3,
            directory = "hooks/playback.session.close/schema-3",
            requestSerializer = SubscriptionHookSpecs.ClosePlayback.requestSerializer,
            resultSerializer = SubscriptionHookSpecs.ClosePlayback.responseSerializer,
        ),
        hookFixture(
            hook = HostHookSpecs.SettingsSchema.hook,
            schemaVersion = 1,
            directory = "hooks/settings.schema.contribute/schema-1",
            requestSerializer = HostHookSpecs.SettingsSchema.requestSerializer,
            resultSerializer = HostHookSpecs.SettingsSchema.responseSerializer,
        ),
        hookFixture(
            hook = HostHookSpecs.EpgRefresh.hook,
            schemaVersion = 4,
            directory = "hooks/epg.content.refresh/schema-4",
            requestSerializer = HostHookSpecs.EpgRefresh.requestSerializer,
            resultSerializer = HostHookSpecs.EpgRefresh.responseSerializer,
        ),
        hookFixture(
            hook = HostHookSpecs.MetadataEnrichment.hook,
            schemaVersion = 3,
            directory = "hooks/metadata.channel.enrich/schema-3",
            requestSerializer = HostHookSpecs.MetadataEnrichment.requestSerializer,
            resultSerializer = HostHookSpecs.MetadataEnrichment.responseSerializer,
        ),
        hookFixture(
            hook = HostHookSpecs.SearchProvider.hook,
            schemaVersion = 4,
            directory = "hooks/search.provider.query/schema-4",
            requestSerializer = HostHookSpecs.SearchProvider.requestSerializer,
            resultSerializer = HostHookSpecs.SearchProvider.responseSerializer,
        ),
        hookFixture(
            hook = HostHookSpecs.BackgroundTask.hook,
            schemaVersion = 2,
            directory = "hooks/background.task.run/schema-2",
            requestSerializer = HostHookSpecs.BackgroundTask.requestSerializer,
            resultSerializer = HostHookSpecs.BackgroundTask.responseSerializer,
        ),
    )

    private val protocolFixtureCatalog = listOf(
        stableFixture("manifests/complete.json", ExtensionManifest.serializer()),
        stableFixture(
            "hooks/subscription.provider.discover/schema-3/request.json",
            SubscriptionHookSpecs.Discover.requestSerializer,
        ),
        stableFixture(
            "hooks/subscription.provider.discover/schema-3/result.json",
            SubscriptionHookSpecs.Discover.responseSerializer,
        ),
        stableFixture(
            "envelopes/invocation-legacy.json",
            SerializedExtensionEnvelope.serializer(),
        ),
        stableFixture(
            "envelopes/invocation-current.json",
            SerializedExtensionEnvelope.serializer(),
        ),
        stableFixture(
            "envelopes/result-success.json",
            SerializedExtensionResult.serializer(),
        ),
        stableFixture(
            "envelopes/result-failure.json",
            SerializedExtensionResult.serializer(),
        ),
        stableFixture(
            "provider-validation-evidence/trusted-direct.json",
            ProviderValidationEvidence.serializer(),
        ),
        stableFixture(
            "provider-validation-evidence/host-broker-receipt.json",
            ProviderValidationEvidence.serializer(),
        ),
        stableFixture("broker/http-invocation.json", BrokerInvocation.serializer()),
        stableFixture("broker/authentication-invocation.json", BrokerInvocation.serializer()),
        stableFixture("broker/http-success.json", BrokerInvocationResult.serializer()),
        stableFixture("broker/authentication-success.json", BrokerInvocationResult.serializer()),
        stableFixture("broker/failure.json", BrokerInvocationResult.serializer()),
        stableFixture("broker/http-request.json", BrokeredHttpRequest.serializer()),
        stableFixture("broker/composed-value.json", BrokerValue.serializer()),
        stableFixture("broker/values/literal.json", BrokerValue.serializer()),
        stableFixture("broker/values/secret.json", BrokerValue.serializer()),
        stableFixture("broker/values/context.json", BrokerValue.serializer()),
        stableFixture("broker/values/concatenated.json", BrokerValue.serializer()),
        stableFixture("broker/values/encoded.json", BrokerValue.serializer()),
        stableFixture(
            "broker/response-value-sources/header.json",
            ResponseValueSource.serializer(),
        ),
        stableFixture(
            "broker/response-value-sources/json-pointer.json",
            ResponseValueSource.serializer(),
        ),
    )

    @Test
    fun `fixture catalog explicitly covers every supported Hook schema`() {
        val fixtureSchemas = hookFixtureCatalog
            .groupBy(HookFixture::hook)
            .mapValues { (_, fixtures) -> fixtures.mapTo(mutableSetOf(), HookFixture::schemaVersion) }

        assertEquals(ExtensionContractCatalog.SupportedHookSchemaVersions, fixtureSchemas)
        hookFixtureCatalog.flatMap(HookFixture::fixtures).forEach { fixture ->
            fixture.verify()
        }
    }

    @Test
    fun `protocol fixture catalog retains every stable encoded shape`() {
        protocolFixtureCatalog.forEach { fixture ->
            fixture.verify()
        }
    }

    @Test
    fun `complete manifest fixture exercises production metadata`() {
        val manifest = decodeFixture(
            "manifests/complete.json",
            ExtensionManifest.serializer(),
        )

        assertTrue(manifest.hooks.isNotEmpty())
        assertTrue(manifest.capabilities.isNotEmpty())
        assertTrue(manifest.settingsSchema?.fields?.isNotEmpty() == true)
        assertTrue(manifest.metadata.isNotEmpty())
        assertTrue(manifest.backgroundTasks.isNotEmpty())
        assertTrue(manifest.networkOrigins.isNotEmpty())
    }

    @Test
    fun `envelope fixtures preserve defaults and ignore future optional fields`() {
        val legacy = decodeFixture(
            "envelopes/invocation-legacy.json",
            SerializedExtensionEnvelope.serializer(),
        )
        assertTrue(legacy.settings.values.isEmpty())
        assertTrue(legacy.grantedCapabilities.isEmpty())
        assertNull(legacy.brokerScope)

        val current = decodeFixture(
            "envelopes/invocation-current.json",
            SerializedExtensionEnvelope.serializer(),
        )
        assertTrue(current.settings.schemaVersions.isNotEmpty())
        assertTrue(current.settings.values.isNotEmpty())
        assertTrue(current.settings.credentialHandles.isNotEmpty())
        assertTrue(current.grantedCapabilities.isNotEmpty())
        assertTrue(current.brokerScope != null)

        val withFutureOptionalField = decodeFixture(
            "envelopes/invocation-with-unknown-optional.json",
            SerializedExtensionEnvelope.serializer(),
        )
        assertEquals(current, withFutureOptionalField)

        val currentHookRequest = decodeFixture(
            "hooks/settings.schema.contribute/schema-1/request.json",
            HostHookSpecs.SettingsSchema.requestSerializer,
        )
        val hookRequestWithFutureOptionalField = decodeFixture(
            "hooks/settings.schema.contribute/schema-1/request-with-unknown-optional.json",
            HostHookSpecs.SettingsSchema.requestSerializer,
        )
        assertEquals(currentHookRequest, hookRequestWithFutureOptionalField)
    }

    @Test
    fun `provider validation evidence fixtures cover every wire branch`() {
        assertIs<ProviderValidationEvidence.TrustedDirect>(
            decodeFixture(
                "provider-validation-evidence/trusted-direct.json",
                ProviderValidationEvidence.serializer(),
            )
        )
        assertIs<ProviderValidationEvidence.HostBrokerReceipt>(
            decodeFixture(
                "provider-validation-evidence/host-broker-receipt.json",
                ProviderValidationEvidence.serializer(),
            )
        )
    }

    @Test
    fun `broker value fixtures cover every wire branch`() {
        val values = listOf(
            decodeFixture("broker/values/literal.json", BrokerValue.serializer()),
            decodeFixture("broker/values/secret.json", BrokerValue.serializer()),
            decodeFixture("broker/values/context.json", BrokerValue.serializer()),
            decodeFixture("broker/values/concatenated.json", BrokerValue.serializer()),
            decodeFixture("broker/values/encoded.json", BrokerValue.serializer()),
        )

        assertIs<BrokerValue.Literal>(values[0])
        assertIs<BrokerValue.Secret>(values[1])
        assertIs<BrokerValue.Context>(values[2])
        assertIs<BrokerValue.Concatenated>(values[3])
        assertIs<BrokerValue.Encoded>(values[4])
    }

    @Test
    fun `response value source fixtures cover every wire branch`() {
        assertIs<ResponseValueSource.Header>(
            decodeFixture(
                "broker/response-value-sources/header.json",
                ResponseValueSource.serializer(),
            )
        )
        assertIs<ResponseValueSource.JsonPointer>(
            decodeFixture(
                "broker/response-value-sources/json-pointer.json",
                ResponseValueSource.serializer(),
            )
        )
    }

    @Test
    fun `broker fixtures cover every operation and result wire branch`() {
        val httpInvocation = decodeFixture(
            "broker/http-invocation.json",
            BrokerInvocation.serializer(),
        )
        val authenticationInvocation = decodeFixture(
            "broker/authentication-invocation.json",
            BrokerInvocation.serializer(),
        )
        assertIs<BrokerOperation.Http>(httpInvocation.operation)
        assertIs<BrokerOperation.Authenticate>(authenticationInvocation.operation)

        val httpSuccess = assertIs<BrokerInvocationResult.Success>(
            decodeFixture("broker/http-success.json", BrokerInvocationResult.serializer())
        )
        val authenticationSuccess = assertIs<BrokerInvocationResult.Success>(
            decodeFixture(
                "broker/authentication-success.json",
                BrokerInvocationResult.serializer(),
            )
        )
        assertIs<BrokerOperationResult.Http>(httpSuccess.result)
        assertIs<BrokerOperationResult.Authentication>(authenticationSuccess.result)
        assertIs<BrokerInvocationResult.Failure>(
            decodeFixture("broker/failure.json", BrokerInvocationResult.serializer())
        )
    }

    private fun <Request : ExtensionPayload, Result : ExtensionPayload> hookFixture(
        hook: Hook,
        schemaVersion: Int,
        directory: String,
        requestSerializer: KSerializer<Request>,
        resultSerializer: KSerializer<Result>,
    ): HookFixture {
        return HookFixture(
            hook = hook,
            schemaVersion = schemaVersion,
            fixtures = listOf(
                stableFixture("$directory/request.json", requestSerializer),
                stableFixture("$directory/result.json", resultSerializer),
            ),
        )
    }

    private fun <Value> stableFixture(
        path: String,
        serializer: KSerializer<Value>,
    ): GoldenFixture = GoldenFixture(path) {
        assertStableFixture(path, serializer)
    }

    private fun <Value> assertStableFixture(
        path: String,
        serializer: KSerializer<Value>,
    ) {
        val source = fixture(path)
        val decoded = json.decodeFromString(serializer, source)
        val encoded = json.encodeToString(serializer, decoded)
        assertEquals(
            json.parseToJsonElement(source),
            json.parseToJsonElement(encoded),
            path,
        )
    }

    private fun <Value> decodeFixture(
        path: String,
        serializer: KSerializer<Value>,
    ): Value = json.decodeFromString(serializer, fixture(path))

    private fun fixture(path: String): String =
        checkNotNull(javaClass.getResource("/golden-wire/v1/$path")) {
            "Missing golden wire fixture: $path"
        }.readText()

    private data class HookFixture(
        val hook: Hook,
        val schemaVersion: Int,
        val fixtures: List<GoldenFixture>,
    )

    private class GoldenFixture(
        val path: String,
        val verify: () -> Unit,
    )
}
