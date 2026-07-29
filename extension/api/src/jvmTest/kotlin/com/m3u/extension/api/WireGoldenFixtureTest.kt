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
            spec = SubscriptionHookSpecs.Discover,
            directory = "hooks/subscription.provider.discover/schema-4",
        ),
        hookFixture(
            spec = SubscriptionHookSpecs.Validate,
            directory = "hooks/subscription.provider.validate/schema-2",
        ),
        hookFixture(
            spec = SubscriptionHookSpecs.Refresh,
            directory = "hooks/subscription.content.refresh/schema-4",
        ),
        hookFixture(
            spec = SubscriptionHookSpecs.ResolvePlayback,
            directory = "hooks/playback.source.resolve/schema-4",
        ),
        hookFixture(
            spec = SubscriptionHookSpecs.ClosePlayback,
            directory = "hooks/playback.session.close/schema-3",
        ),
        hookFixture(
            spec = HostHookSpecs.SettingsSchema,
            directory = "hooks/settings.schema.contribute/schema-1",
        ),
        hookFixture(
            spec = HostHookSpecs.EpgRefresh,
            directory = "hooks/epg.content.refresh/schema-4",
        ),
        hookFixture(
            spec = HostHookSpecs.MetadataEnrichment,
            directory = "hooks/metadata.channel.enrich/schema-3",
        ),
        hookFixture(
            spec = HostHookSpecs.SearchProvider,
            directory = "hooks/search.provider.query/schema-4",
        ),
        hookFixture(
            spec = HostHookSpecs.BackgroundTask,
            directory = "hooks/background.task.run/schema-2",
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
            .groupBy { fixture -> fixture.spec.hook }
            .mapValues { (_, fixtures) ->
                fixtures.mapTo(mutableSetOf()) { fixture -> fixture.spec.schemaVersion }
            }

        assertEquals(ExtensionContractCatalog.SupportedHookSchemaVersions, fixtureSchemas)
        assertTrue(
            hookFixtureCatalog.all { fixture ->
                ExtensionContractCatalog.containsCanonical(fixture.spec)
            }
        )
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
        assertNull(legacy.invocationBudget)

        val current = decodeFixture(
            "envelopes/invocation-current.json",
            SerializedExtensionEnvelope.serializer(),
        )
        assertTrue(current.settings.schemaVersions.isNotEmpty())
        assertTrue(current.settings.values.isNotEmpty())
        assertTrue(current.settings.credentialHandles.isNotEmpty())
        assertTrue(current.grantedCapabilities.isNotEmpty())
        assertTrue(current.brokerScope != null)
        val expectedBudget = ExtensionInvocationBudget(
            remainingTimeMillis = 30_000,
            maxBrokerRequests = 16,
            maxBrokerRequestBytes = 4_194_304,
            maxBrokerResponseBytes = 16_777_216,
        )
        assertEquals(expectedBudget, current.invocationBudget)

        val withFutureOptionalField = decodeFixture(
            "envelopes/invocation-with-unknown-optional.json",
            SerializedExtensionEnvelope.serializer(),
        )
        assertEquals(current, withFutureOptionalField)
        assertEquals(expectedBudget, withFutureOptionalField.invocationBudget)

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
        spec: HookSpec<Request, Result>,
        directory: String,
    ): HookFixture {
        return HookFixture(
            spec = spec,
            fixtures = listOf(
                stableFixture("$directory/request.json", spec.requestSerializer),
                stableFixture("$directory/result.json", spec.responseSerializer),
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
        val spec: HookSpec<*, *>,
        val fixtures: List<GoldenFixture>,
    )

    private class GoldenFixture(
        val path: String,
        val verify: () -> Unit,
    )
}
