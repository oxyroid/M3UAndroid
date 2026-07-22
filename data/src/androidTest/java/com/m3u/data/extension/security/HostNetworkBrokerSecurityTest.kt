package com.m3u.data.extension.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m3u.data.database.model.ProviderAccount
import com.m3u.data.database.model.ProviderCredentialEntity
import com.m3u.extension.api.ExtensionHookIds
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.Hook
import com.m3u.extension.api.security.BrokerAuthenticationRequest
import com.m3u.extension.api.security.BrokerErrorCodes
import com.m3u.extension.api.security.BrokerHttpExchange
import com.m3u.extension.api.security.BrokerScopeHandle
import com.m3u.extension.api.security.BrokerValue
import com.m3u.extension.api.security.BrokerValueEncoding
import com.m3u.extension.api.security.BrokeredHttpRequest
import com.m3u.extension.api.security.ContextReference
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.api.security.OpaqueContextCapture
import com.m3u.extension.api.security.ResponseValueSource
import com.m3u.extension.api.security.SecretReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class HostNetworkBrokerSecurityTest {
    @Test
    fun brokerEnforcesScopeOriginAndAuthenticationBoundaries() = runBlocking {
        var observedAuthorization: String? = null
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            observedAuthorization = chain.request().header("Authorization")
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("Set-Cookie", "session=must-not-leak")
                .header("X-Auth-Token", "must-not-leak")
                .header("ETag", "safe-etag")
                .body("""{"value":"safe","accessToken":"must-not-leak"}""".toResponseBody())
                .build()
        }.build()
        val fixture = accountFixture(client)

        assertFails {
            fixture.broker.execute(
                scope = fixture.scope,
                principal = WRONG_PRINCIPAL,
                hook = ACCOUNT_HOOK,
                request = request("$BASE_URL/items"),
            )
        }
        assertFails {
            fixture.broker.execute(
                scope = fixture.scope,
                principal = PRINCIPAL,
                hook = ExtensionHookIds.SubscriptionContentRefresh,
                request = request("$BASE_URL/items"),
            )
        }
        assertFails {
            fixture.broker.execute(
                scope = fixture.scope,
                principal = PRINCIPAL,
                hook = ACCOUNT_HOOK,
                request = request("https://evil.example/items"),
            )
        }
        assertFails {
            fixture.broker.execute(
                scope = fixture.scope,
                principal = PRINCIPAL,
                hook = ACCOUNT_HOOK,
                request = request(
                    "$BASE_URL/items",
                    headers = mapOf(
                        "authorization" to BrokerValue.Literal("Bearer stolen")
                    ),
                ),
            )
        }
        assertFails {
            fixture.broker.execute(
                scope = fixture.scope,
                principal = PRINCIPAL,
                hook = ACCOUNT_HOOK,
                request = request(
                    "$BASE_URL/items",
                    headers = mapOf(
                        "Authorization" to BrokerValue.Secret(
                            SecretReference(CredentialHandle("persistent:outside-scope"))
                        )
                    ),
                ),
            )
        }

        val response = fixture.broker.execute(
            scope = fixture.scope,
            principal = PRINCIPAL,
            hook = ACCOUNT_HOOK,
            request = request(
                "$BASE_URL/items",
                headers = mapOf(
                    "Authorization" to BrokerValue.Secret(
                        SecretReference(fixture.credentialHandle)
                    )
                ),
            ),
        )

        assertEquals("secret-token", observedAuthorization)
        assertTrue(response.body.contains("safe"))
        assertFalse(response.body.contains("must-not-leak"))
        assertFalse(response.headers.keys.any { it.equals("Set-Cookie", ignoreCase = true) })
        assertFalse(response.headers.keys.any { it.equals("X-Auth-Token", ignoreCase = true) })
        assertEquals("safe-etag", response.headers["ETag"])
    }

    @Test
    fun brokerEncodesComposedCredentialValuesInsideHost() = runBlocking {
        val secret = "p\"a\\ss\n& +"
        var observedAuthorization: String? = null
        var observedFormValue: String? = null
        var observedBody: String? = null
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            observedAuthorization = chain.request().header("Authorization")
            observedFormValue = chain.request().header("X-Form-Value")
            observedBody = chain.request().body?.let { body ->
                Buffer().also(body::writeTo).readUtf8()
            }
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody())
                .build()
        }.build()
        val fixture = accountFixture(client, secret)
        val secretValue = BrokerValue.Secret(SecretReference(fixture.credentialHandle))

        fixture.broker.execute(
            scope = fixture.scope,
            principal = PRINCIPAL,
            hook = ACCOUNT_HOOK,
            request = BrokeredHttpRequest(
                method = "POST",
                url = "$BASE_URL/items",
                headers = mapOf(
                    "Content-Type" to BrokerValue.Literal("application/json"),
                    "Authorization" to BrokerValue.Concatenated(
                        listOf(
                            BrokerValue.Literal("Basic "),
                            BrokerValue.Encoded(
                                value = BrokerValue.Concatenated(
                                    listOf(BrokerValue.Literal("user:"), secretValue)
                                ),
                                encoding = BrokerValueEncoding.Base64,
                            ),
                        )
                    ),
                    "X-Form-Value" to BrokerValue.Encoded(
                        value = secretValue,
                        encoding = BrokerValueEncoding.FormUrlComponent,
                    ),
                ),
                body = listOf(
                    BrokerValue.Literal("{\"password\":"),
                    BrokerValue.Encoded(
                        value = secretValue,
                        encoding = BrokerValueEncoding.JsonString,
                    ),
                    BrokerValue.Literal("}"),
                ),
            ),
        )

        val basic = checkNotNull(observedAuthorization).removePrefix("Basic ")
        assertEquals(
            "user:$secret",
            String(android.util.Base64.decode(basic, android.util.Base64.NO_WRAP)),
        )
        assertEquals("p%22a%5Css%0A%26+%2B", observedFormValue)
        assertEquals(secret, Json.parseToJsonElement(checkNotNull(observedBody))
            .jsonObject.getValue("password").jsonPrimitive.content)
    }

    @Test
    fun brokerRejectsCrossOriginRedirect() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(302)
                .message("Found")
                .header("Location", "https://evil.example/token")
                .body("".toResponseBody())
                .build()
        }.build()
        val fixture = accountFixture(client)

        assertFails {
            fixture.broker.execute(
                scope = fixture.scope,
                principal = PRINCIPAL,
                hook = ACCOUNT_HOOK,
                request = request("$BASE_URL/login"),
            )
        }
    }

    @Test
    fun cancellationCancelsInFlightHttpCall() = runBlocking {
        val cancellationObserved = AtomicBoolean(false)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            while (!chain.call().isCanceled()) {
                Thread.sleep(5)
            }
            cancellationObserved.set(true)
            throw IOException("cancelled")
        }.build()
        val fixture = accountFixture(client)

        assertFails {
            withTimeout(100L) {
                fixture.broker.execute(
                    scope = fixture.scope,
                    principal = PRINCIPAL,
                    hook = ACCOUNT_HOOK,
                    request = request("$BASE_URL/slow"),
                )
            }
        }

        withTimeout(5_000L) {
            while (!cancellationObserved.get()) delay(10L)
        }
        assertTrue(cancellationObserved.get())
    }

    @Test
    fun brokerClassifiesNetworkAndResponseSizeFailuresWithoutLeakingCauses() = runBlocking {
        val networkFixture = accountFixture(
            OkHttpClient.Builder().addInterceptor {
                throw IOException(SECRET_FAILURE_MESSAGE)
            }.build()
        )

        val networkFailure = brokerFailure {
            networkFixture.broker.execute(
                scope = networkFixture.scope,
                principal = PRINCIPAL,
                hook = ACCOUNT_HOOK,
                request = request("$BASE_URL/items"),
            )
        }

        assertEquals(BrokerErrorCodes.NetworkFailed, networkFailure.code)
        assertTrue(networkFailure.recoverable)
        assertNull(networkFailure.message)

        val oversizedFixture = accountFixture(
            OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("response-is-too-large".toResponseBody())
                    .build()
            }.build()
        )

        val sizeFailure = brokerFailure {
            oversizedFixture.broker.execute(
                scope = oversizedFixture.scope,
                principal = PRINCIPAL,
                hook = ACCOUNT_HOOK,
                request = request("$BASE_URL/items").copy(maximumResponseBytes = 4),
            )
        }

        assertEquals(BrokerErrorCodes.ResponseTooLarge, sizeFailure.code)
        assertFalse(sizeFailure.recoverable)
        assertNull(sizeFailure.message)
    }

    @Test
    fun authenticationReturnsOnlyOpaqueReceiptAndStoresCredentialAndContexts() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header(CAPTURE_HEADER, "captured-secret")
                .header("Set-Cookie", "session=must-not-cross-ipc")
                .body(
                    """{"accessToken":"must-remain-opaque","server_id":"server-1","user_id":"user-1"}"""
                        .toResponseBody()
                )
                .build()
        }.build()
        val vault = FakeCredentialVault()
        val registry = ActiveExtensionPrincipalRegistry().apply { activate(PRINCIPAL) }
        val store = scopeStore(vault, registry)
        val scope = store.mintAuthenticationScope(
            principal = PRINCIPAL,
            approvedBaseUrl = BASE_URL,
            transientCredentials = emptyMap(),
        )
        val broker = HostNetworkBrokerImpl(client, store)

        val response = broker.authenticate(
            scope = scope,
            principal = PRINCIPAL,
            hook = ExtensionHookIds.SubscriptionProviderValidate,
            request = BrokerAuthenticationRequest(
                exchange = BrokerHttpExchange(method = "GET", url = "$BASE_URL/login"),
                primaryCredentialSource = ResponseValueSource.Header(CAPTURE_HEADER),
                opaqueContexts = listOf(
                    OpaqueContextCapture(
                        key = "server_id",
                        source = ResponseValueSource.JsonPointer("/server_id"),
                    ),
                    OpaqueContextCapture(
                        key = "user_id",
                        source = ResponseValueSource.JsonPointer("/user_id"),
                    ),
                    OpaqueContextCapture(
                        key = "opaque_probe",
                        source = ResponseValueSource.JsonPointer("/accessToken"),
                    ),
                ),
            ),
        )

        val encodedResponse = Json.encodeToString(response)
        assertFalse(encodedResponse.contains("captured-secret"))
        assertFalse(encodedResponse.contains("must-remain-opaque"))
        assertFalse(encodedResponse.contains("must-not-cross-ipc"))
        assertFalse(encodedResponse.contains("server-1"))
        assertFalse(encodedResponse.contains("user-1"))
        val authentication = store.consumeAuthenticationReceipt(
            scope = scope,
            principal = PRINCIPAL,
            receipt = checkNotNull(response.receipt),
        )
        assertTrue(authentication.credentialHandle.value.startsWith("broker-captured:"))
        assertEquals(
            "captured-secret",
            store.resolveCredential(
                scope = scope,
                principal = PRINCIPAL,
                hook = ExtensionHookIds.SubscriptionProviderValidate,
                handle = authentication.credentialHandle,
            ),
        )
        assertEquals(
            "server-1",
            store.resolveContext(
                scope = scope,
                principal = PRINCIPAL,
                hook = ExtensionHookIds.SubscriptionProviderValidate,
                reference = ContextReference("server_id"),
            ),
        )
        assertEquals(
            "must-remain-opaque",
            store.resolveContext(
                scope = scope,
                principal = PRINCIPAL,
                hook = ExtensionHookIds.SubscriptionProviderValidate,
                reference = ContextReference("opaque_probe"),
            ),
        )
        assertFails {
            store.consumeAuthenticationReceipt(
                scope = scope,
                principal = PRINCIPAL,
                receipt = checkNotNull(response.receipt),
            )
        }
    }

    @Test
    fun ordinaryHttpCannotRunInsideAuthenticationScope() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"value":"safe"}""".toResponseBody())
                .build()
        }.build()
        val vault = FakeCredentialVault()
        val registry = ActiveExtensionPrincipalRegistry().apply { activate(PRINCIPAL) }
        val store = scopeStore(vault, registry)
        val scope = store.mintAuthenticationScope(
            principal = PRINCIPAL,
            approvedBaseUrl = BASE_URL,
            transientCredentials = emptyMap(),
        )

        val failure = brokerFailure {
            HostNetworkBrokerImpl(client, store).execute(
                scope = scope,
                principal = PRINCIPAL,
                hook = ExtensionHookIds.SubscriptionProviderValidate,
                request = BrokeredHttpRequest(method = "POST", url = "$BASE_URL/login"),
            )
        }

        assertEquals(BrokerErrorCodes.InvalidRequest, failure.code)
    }

    @Test
    fun authenticationScopeAllowsOnlyOneSuccessfulAuthentication() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(
                    """{"credential":"captured"}"""
                        .toResponseBody()
                )
                .build()
        }.build()
        val vault = FakeCredentialVault()
        val registry = ActiveExtensionPrincipalRegistry().apply { activate(PRINCIPAL) }
        val store = scopeStore(vault, registry)
        val scope = store.mintAuthenticationScope(
            principal = PRINCIPAL,
            approvedBaseUrl = BASE_URL,
            transientCredentials = emptyMap(),
        )

        val request = BrokerAuthenticationRequest(
            exchange = BrokerHttpExchange(method = "POST", url = "$BASE_URL/login"),
            primaryCredentialSource = ResponseValueSource.JsonPointer("/credential"),
        )
        val broker = HostNetworkBrokerImpl(client, store)
        broker.authenticate(
            scope = scope,
            principal = PRINCIPAL,
            hook = ExtensionHookIds.SubscriptionProviderValidate,
            request = request,
        )

        val failure = brokerFailure {
            broker.authenticate(
                scope = scope,
                principal = PRINCIPAL,
                hook = ExtensionHookIds.SubscriptionProviderValidate,
                request = request,
            )
        }

        assertEquals(BrokerErrorCodes.InvalidRequest, failure.code)
    }

    @Test
    fun failedAuthenticationReturnsOnlyStatusAndDoesNotCaptureCredential() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized")
                .body(
                    """{"message":"Invalid credentials","accessToken":"must-not-capture"}"""
                        .toResponseBody()
                )
                .build()
        }.build()
        val vault = FakeCredentialVault()
        val registry = ActiveExtensionPrincipalRegistry().apply { activate(PRINCIPAL) }
        val store = scopeStore(vault, registry)
        val scope = store.mintAuthenticationScope(
            principal = PRINCIPAL,
            approvedBaseUrl = BASE_URL,
            transientCredentials = emptyMap(),
        )
        val broker = HostNetworkBrokerImpl(client, store)

        val response = broker.authenticate(
            scope = scope,
            principal = PRINCIPAL,
            hook = ExtensionHookIds.SubscriptionProviderValidate,
            request = BrokerAuthenticationRequest(
                exchange = BrokerHttpExchange(method = "POST", url = "$BASE_URL/login"),
                primaryCredentialSource = ResponseValueSource.JsonPointer("/accessToken"),
            ),
        )

        assertEquals(401, response.statusCode)
        assertNull(response.receipt)
        val encodedResponse = Json.encodeToString(response)
        assertFalse(encodedResponse.contains("Invalid credentials"))
        assertFalse(encodedResponse.contains("must-not-capture"))
    }

    private fun accountFixture(
        client: OkHttpClient,
        secret: String = "secret-token",
    ): AccountFixture {
        val vault = FakeCredentialVault()
        val registry = ActiveExtensionPrincipalRegistry().apply { activate(PRINCIPAL) }
        val store = scopeStore(vault, registry)
        val account = ProviderAccount(
            id = ACCOUNT_ID,
            providerId = PRINCIPAL.extensionId.value,
            providerKind = "reference",
            baseUrl = BASE_URL,
            serverId = "server",
            serverName = "Server",
            serverVersion = "1",
            userId = "user",
            username = "name",
            playlistUrl = PLAYLIST_URL,
            ownerPackageName = PRINCIPAL.packageName,
            ownerServiceName = PRINCIPAL.serviceName,
            ownerCertificateSha256 = PRINCIPAL.certificateSha256,
        )
        val credential = vault.encrypt(
            accountId = account.id,
            secret = secret,
            credentialHandle = "persistent:account-token",
        )
        val scope = store.mintAccountScope(
            principal = PRINCIPAL,
            allowedHook = ACCOUNT_HOOK,
            account = account,
            credential = credential,
        )
        return AccountFixture(
            broker = HostNetworkBrokerImpl(client, store),
            scope = scope,
            credentialHandle = CredentialHandle(credential.credentialHandle),
        )
    }

    private fun scopeStore(
        vault: CredentialVault,
        registry: ActiveExtensionPrincipalRegistry,
    ): ProviderBrokerScopeStore {
        var nextId = 0
        return ProviderBrokerScopeStore(
            credentialVault = vault,
            principalRegistry = registry,
            clock = { 1_000L },
            idFactory = { "security-${nextId++}" },
            defaultTtlMillis = 60_000L,
        )
    }

    private fun request(
        url: String,
        headers: Map<String, BrokerValue> = emptyMap(),
    ) = BrokeredHttpRequest(method = "GET", url = url, headers = headers)

    private suspend fun assertFails(block: suspend () -> Unit) {
        assertTrue(runCatching { block() }.isFailure)
    }

    private suspend fun brokerFailure(
        block: suspend () -> Unit,
    ): ProviderBrokerException = try {
        block()
        throw AssertionError("Expected ProviderBrokerException")
    } catch (failure: ProviderBrokerException) {
        failure
    }

    private data class AccountFixture(
        val broker: HostNetworkBrokerImpl,
        val scope: BrokerScopeHandle,
        val credentialHandle: CredentialHandle,
    )

    private class FakeCredentialVault : CredentialVault {
        private val persistentSecrets = linkedMapOf<Pair<String, String>, String>()
        private val transientSecrets = linkedMapOf<String, String>()
        private var nextTransientId = 0

        override fun encrypt(
            accountId: String,
            secret: String,
            credentialHandle: String?,
        ): ProviderCredentialEntity {
            val handle = credentialHandle ?: "persistent:$accountId"
            persistentSecrets[accountId to handle] = secret
            return ProviderCredentialEntity(
                accountId = accountId,
                credentialHandle = handle,
                ciphertext = "fake-ciphertext",
                nonce = "fake-nonce",
                keyVersion = 1,
            )
        }

        override fun decrypt(credential: ProviderCredentialEntity): String? =
            persistentSecrets[credential.accountId to credential.credentialHandle]

        override fun stage(secret: String): CredentialHandle {
            val handle = CredentialHandle("transient:${nextTransientId++}")
            transientSecrets[handle.value] = secret
            return handle
        }

        override fun consume(handle: CredentialHandle): String? = transientSecrets.remove(handle.value)
    }

    private companion object {
        const val ACCOUNT_ID = "account"
        const val BASE_URL = "https://media.example.test"
        const val PLAYLIST_URL = "m3u-provider://account/account/live"
        const val CAPTURE_HEADER = "X-Provider-Token"
        const val SECRET_FAILURE_MESSAGE = "token=must-not-cross-ipc"
        val ACCOUNT_HOOK: Hook = ExtensionHookIds.PlaybackSourceResolve
        val EXTENSION_ID = ExtensionId("com.m3u.reference.provider")
        val PRINCIPAL = ExtensionPrincipal(
            extensionId = EXTENSION_ID,
            packageName = "com.m3u.reference.provider",
            serviceName = "com.m3u.reference.provider.ExtensionService",
            certificateSha256 = "11".repeat(32),
            uid = 10_001,
        )
        val WRONG_PRINCIPAL = PRINCIPAL.copy(
            packageName = "com.m3u.reference.provider.reinstalled",
            serviceName = "com.m3u.reference.provider.reinstalled.ExtensionService",
            certificateSha256 = "22".repeat(32),
            uid = 10_002,
        )
    }
}
