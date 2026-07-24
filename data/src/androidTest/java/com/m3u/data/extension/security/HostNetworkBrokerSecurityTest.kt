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
import kotlinx.serialization.json.jsonArray
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
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("Set-Cookie", "session=must-not-leak")
                .header("X-Auth-Token", "must-not-leak")
                .header("ETag", "safe-etag")
                .body(
                    """
                    {
                      "value": "safe",
                      "accessToken": "must-not-leak",
                      "nextPageToken": "page-token",
                      "continuationToken": "continuation-token",
                      "tokenType": "Bearer"
                    }
                    """.trimIndent().toResponseBody()
                )
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
            request = request("$BASE_URL/items"),
        )

        val responseBody = Json.parseToJsonElement(response.body).jsonObject
        assertEquals("safe", responseBody.getValue("value").jsonPrimitive.content)
        assertEquals("***", responseBody.getValue("accessToken").jsonPrimitive.content)
        assertEquals("page-token", responseBody.getValue("nextPageToken").jsonPrimitive.content)
        assertEquals(
            "continuation-token",
            responseBody.getValue("continuationToken").jsonPrimitive.content,
        )
        assertEquals("Bearer", responseBody.getValue("tokenType").jsonPrimitive.content)
        assertFalse(response.headers.keys.any { it.equals("Set-Cookie", ignoreCase = true) })
        assertFalse(response.headers.keys.any { it.equals("X-Auth-Token", ignoreCase = true) })
        assertEquals("safe-etag", response.headers["ETag"])
    }

    @Test
    fun brokerPreservesOriginalJsonWhenNoSensitiveFieldOrValueNeedsRedaction() = runBlocking {
        val serverBody =
            """
            {
              "nextPageToken": "page-token",
              "continuationToken": "continuation-token",
              "tokenType": "Bearer",
              "tokenExpiry": 3600,
              "credentialType": "opaque",
              "secretary": "Ada"
            }
            """.trimIndent()
        val fixture = accountFixture(
            OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(serverBody.toResponseBody())
                    .build()
            }.build()
        )

        val response = fixture.broker.execute(
            scope = fixture.scope,
            principal = PRINCIPAL,
            hook = ACCOUNT_HOOK,
            request = request("$BASE_URL/items"),
        )

        assertEquals(serverBody, response.body)
    }

    @Test
    fun brokerRedactsAuthenticationFieldsAfterUtf8Bom() = runBlocking {
        val fixture = accountFixture(
            OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        (
                            "\uFEFF" +
                                """{"accessToken":"fresh-token","nextPageToken":"page-token"}"""
                        ).toResponseBody()
                    )
                    .build()
            }.build()
        )

        val response = fixture.broker.execute(
            scope = fixture.scope,
            principal = PRINCIPAL,
            hook = ACCOUNT_HOOK,
            request = request("$BASE_URL/items"),
        )

        val responseBody = Json.parseToJsonElement(response.body).jsonObject
        assertEquals("***", responseBody.getValue("accessToken").jsonPrimitive.content)
        assertEquals("page-token", responseBody.getValue("nextPageToken").jsonPrimitive.content)
    }

    @Test
    fun brokerPreservesUtf8BomWhenJsonNeedsNoRedaction() = runBlocking {
        val serverBody = "\uFEFF" + """{"nextPageToken":"page-token"}"""
        val fixture = accountFixture(
            OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(serverBody.toResponseBody())
                    .build()
            }.build()
        )

        val response = fixture.broker.execute(
            scope = fixture.scope,
            principal = PRINCIPAL,
            hook = ACCOUNT_HOOK,
            request = request("$BASE_URL/items"),
        )

        assertEquals(serverBody, response.body)
    }

    @Test
    fun brokerRedactsOnlyTheClosedAuthenticationFieldSet() = runBlocking {
        val fixture = accountFixture(
            OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """
                        {
                          "access_token": "access",
                          "refreshToken": "refresh",
                          "client-secret": "client",
                          "apiKey": "api",
                          "password": "password",
                          "nextPageToken": "page",
                          "continuationToken": "continuation",
                          "credentialType": "opaque",
                          "secretary": "Ada"
                        }
                        """.trimIndent().toResponseBody()
                    )
                    .build()
            }.build()
        )

        val response = fixture.broker.execute(
            scope = fixture.scope,
            principal = PRINCIPAL,
            hook = ACCOUNT_HOOK,
            request = request("$BASE_URL/items"),
        )

        val responseBody = Json.parseToJsonElement(response.body).jsonObject
        listOf(
            "access_token",
            "refreshToken",
            "client-secret",
            "apiKey",
            "password",
        ).forEach { key ->
            assertEquals("***", responseBody.getValue(key).jsonPrimitive.content)
        }
        assertEquals("page", responseBody.getValue("nextPageToken").jsonPrimitive.content)
        assertEquals(
            "continuation",
            responseBody.getValue("continuationToken").jsonPrimitive.content,
        )
        assertEquals("opaque", responseBody.getValue("credentialType").jsonPrimitive.content)
        assertEquals("Ada", responseBody.getValue("secretary").jsonPrimitive.content)
    }

    @Test
    fun accountBrokerInjectsScopedCredentialAndContextWithoutReturningTheirValues() = runBlocking {
        var observedPath: String? = null
        var observedAuthorization: String? = null
        var observedBody: String? = null
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            observedPath = chain.request().url.encodedPath
            observedAuthorization = chain.request().header("Authorization")
            observedBody = chain.request().body?.let { body ->
                Buffer().also(body::writeTo).readUtf8()
            }
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("ETag", "secret-token")
                .body(
                    """{"token":"secret-token","user":"opaque-user","value":"safe"}"""
                        .toResponseBody()
                )
                .build()
        }.build()
        val fixture = accountFixture(client)
        val credential = BrokerValue.Secret(
            SecretReference(CredentialHandle("persistent:account-token"))
        )
        val user = BrokerValue.Context(ContextReference("user_id"))

        val response = fixture.broker.execute(
            scope = fixture.scope,
            principal = PRINCIPAL,
            hook = ACCOUNT_HOOK,
            request = BrokeredHttpRequest(
                method = "POST",
                url = BrokerValue.Concatenated(
                    listOf(
                        BrokerValue.Literal("$BASE_URL/users/"),
                        BrokerValue.Encoded(user, BrokerValueEncoding.FormUrlComponent),
                        BrokerValue.Literal("/items"),
                    )
                ),
                headers = mapOf(
                    "Authorization" to BrokerValue.Concatenated(
                        listOf(BrokerValue.Literal("Bearer "), credential)
                    ),
                    "Content-Type" to BrokerValue.Literal("application/json"),
                ),
                body = listOf(
                    BrokerValue.Literal("""{"user":"""),
                    BrokerValue.Encoded(user, BrokerValueEncoding.JsonString),
                    BrokerValue.Literal(""","token":"""),
                    BrokerValue.Encoded(credential, BrokerValueEncoding.JsonString),
                    BrokerValue.Literal("}"),
                ),
            ),
        )

        assertEquals("/users/opaque-user/items", observedPath)
        assertEquals("Bearer secret-token", observedAuthorization)
        assertEquals(
            """{"user":"opaque-user","token":"secret-token"}""",
            observedBody,
        )
        val responseBody = Json.parseToJsonElement(response.body).jsonObject
        assertEquals("***", responseBody.getValue("token").jsonPrimitive.content)
        assertEquals("***", responseBody.getValue("user").jsonPrimitive.content)
        assertEquals("safe", responseBody.getValue("value").jsonPrimitive.content)
        assertFalse(response.body.contains("secret-token"))
        assertFalse(response.body.contains("opaque-user"))
        assertNull(response.headers["ETag"])
    }

    @Test
    fun brokerRedactsResolvedValuesUsedAsJsonObjectKeys() = runBlocking {
        val fixture = accountFixture(
            OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"secret-token":"value"}""".toResponseBody())
                    .build()
            }.build()
        )

        val response = fixture.broker.execute(
            scope = fixture.scope,
            principal = PRINCIPAL,
            hook = ACCOUNT_HOOK,
            request = BrokeredHttpRequest(
                method = "GET",
                url = "$BASE_URL/items",
                headers = mapOf(
                    "Authorization" to BrokerValue.Secret(
                        SecretReference(CredentialHandle("persistent:account-token"))
                    )
                ),
            ),
        )

        assertEquals(
            "value",
            Json.parseToJsonElement(response.body)
                .jsonObject
                .getValue("***")
                .jsonPrimitive
                .content,
        )
        assertFalse(response.body.contains("secret-token"))
    }

    @Test
    fun brokerKeepsRootAndArrayJsonValidWhileRedactingResolvedValues() = runBlocking {
        val responseBodies = ArrayDeque(
            listOf(
                "\"prefix-secret-token-suffix\"",
                """["opaque-user","safe"]""",
            )
        )
        val fixture = accountFixture(
            OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(responseBodies.removeFirst().toResponseBody())
                    .build()
            }.build()
        )
        val request = BrokeredHttpRequest(
            method = "GET",
            url = "$BASE_URL/items",
            headers = mapOf(
                "Authorization" to BrokerValue.Secret(
                    SecretReference(CredentialHandle("persistent:account-token"))
                ),
                "X-Provider-User" to BrokerValue.Context(ContextReference("user_id")),
            ),
        )

        val root = fixture.broker.execute(
            scope = fixture.scope,
            principal = PRINCIPAL,
            hook = ACCOUNT_HOOK,
            request = request,
        )
        val array = fixture.broker.execute(
            scope = fixture.scope,
            principal = PRINCIPAL,
            hook = ACCOUNT_HOOK,
            request = request,
        )

        assertEquals(
            "prefix-***-suffix",
            Json.parseToJsonElement(root.body).jsonPrimitive.content,
        )
        assertEquals(
            "***",
            Json.parseToJsonElement(array.body).jsonArray.first().jsonPrimitive.content,
        )
        assertEquals(
            "safe",
            Json.parseToJsonElement(array.body).jsonArray.last().jsonPrimitive.content,
        )
    }

    @Test
    fun brokerRejectsJsonKeyCollisionsCreatedBySensitiveValueRedaction() = runBlocking {
        val fixture = accountFixture(
            OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"secret-token":"first","***":"second"}""".toResponseBody())
                    .build()
            }.build()
        )

        val failure = brokerFailure {
            fixture.broker.execute(
                scope = fixture.scope,
                principal = PRINCIPAL,
                hook = ACCOUNT_HOOK,
                request = BrokeredHttpRequest(
                    method = "GET",
                    url = "$BASE_URL/items",
                    headers = mapOf(
                        "Authorization" to BrokerValue.Secret(
                            SecretReference(CredentialHandle("persistent:account-token"))
                        )
                    ),
                ),
            )
        }

        assertEquals(BrokerErrorCodes.InvalidRequest, failure.code)
        assertFalse(failure.recoverable)
    }

    @Test
    fun brokerRedactsTheUnionOfOverlappingSensitiveValues() = runBlocking {
        val fixture = accountFixture(
            client = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("abcde".toResponseBody())
                    .build()
            }.build(),
            primaryCredential = "ab",
            userId = "bcde",
        )

        val response = fixture.broker.execute(
            scope = fixture.scope,
            principal = PRINCIPAL,
            hook = ACCOUNT_HOOK,
            request = BrokeredHttpRequest(
                method = "GET",
                url = "$BASE_URL/items",
                headers = mapOf(
                    "X-Primary" to BrokerValue.Secret(
                        SecretReference(CredentialHandle("persistent:account-token"))
                    ),
                    "X-Context" to BrokerValue.Context(ContextReference("user_id")),
                ),
            ),
        )

        assertEquals("***", response.body)
    }

    @Test
    fun authenticationHandlesUtf8BomWhileEncodingCredentialsAndReturningOnlyAReceipt() = runBlocking {
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
                .body(("\uFEFF" + """{"accessToken":"captured-token"}""").toResponseBody())
                .build()
        }.build()
        val vault = FakeCredentialVault()
        val registry = ActiveExtensionPrincipalRegistry().apply { activate(PRINCIPAL) }
        val store = scopeStore(vault, registry)
        val password = vault.stage(secret)
        val scope = store.mintAuthenticationScope(
            principal = PRINCIPAL,
            approvedBaseUrl = BASE_URL,
            transientCredentials = mapOf("password" to password),
        )
        val secretValue = BrokerValue.Secret(SecretReference(password))

        val response = HostNetworkBrokerImpl(client, store).authenticate(
            scope = scope,
            principal = PRINCIPAL,
            hook = ExtensionHookIds.SubscriptionProviderValidate,
            request = BrokerAuthenticationRequest(
                exchange = BrokerHttpExchange(
                    method = "POST",
                    url = "$BASE_URL/login",
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
                primaryCredentialSource = ResponseValueSource.JsonPointer("/accessToken"),
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
        assertEquals(200, response.statusCode)
        assertTrue(response.receipt != null)
        assertFalse(Json.encodeToString(response).contains("captured-token"))
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
    fun brokerRejectsResponseBeyondWireSafeBodyLimit() = runBlocking {
        val fixture = accountFixture(
            OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("x".repeat(721 * 1024).toResponseBody())
                    .build()
            }.build()
        )

        val failure = brokerFailure {
            fixture.broker.execute(
                scope = fixture.scope,
                principal = PRINCIPAL,
                hook = ACCOUNT_HOOK,
                request = request("$BASE_URL/items").copy(
                    maximumResponseBytes = 4 * 1024 * 1024
                ),
            )
        }

        assertEquals(BrokerErrorCodes.ResponseTooLarge, failure.code)
        assertFalse(failure.recoverable)
    }

    @Test
    fun deeplyNestedServerJsonIsRejectedBeforeTreeParsing() = runBlocking {
        val deeplyNestedBody = nestedArrays(depth = 65, value = "\"captured\"")
        val accountFixture = accountFixture(
            OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(deeplyNestedBody.toResponseBody())
                    .build()
            }.build()
        )
        val ordinaryFailure = brokerFailure {
            accountFixture.broker.execute(
                scope = accountFixture.scope,
                principal = PRINCIPAL,
                hook = ACCOUNT_HOOK,
                request = request("$BASE_URL/items"),
            )
        }
        assertEquals(BrokerErrorCodes.ResponseTooLarge, ordinaryFailure.code)

        val vault = FakeCredentialVault()
        val registry = ActiveExtensionPrincipalRegistry().apply { activate(PRINCIPAL) }
        val store = scopeStore(vault, registry)
        val authenticationScope = store.mintAuthenticationScope(
            principal = PRINCIPAL,
            approvedBaseUrl = BASE_URL,
            transientCredentials = emptyMap(),
        )
        val authenticationFailure = brokerFailure {
            HostNetworkBrokerImpl(
                OkHttpClient.Builder().addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(deeplyNestedBody.toResponseBody())
                        .build()
                }.build(),
                store,
            ).authenticate(
                scope = authenticationScope,
                principal = PRINCIPAL,
                hook = ExtensionHookIds.SubscriptionProviderValidate,
                request = BrokerAuthenticationRequest(
                    exchange = BrokerHttpExchange(method = "GET", url = "$BASE_URL/login"),
                    primaryCredentialSource = ResponseValueSource.JsonPointer(""),
                ),
            )
        }
        assertEquals(BrokerErrorCodes.ResponseTooLarge, authenticationFailure.code)
    }

    @Test
    fun brokerBoundsSensitiveRedactionVariantsBeforeSendingRequest() = runBlocking {
        var requestReachedServer = false
        val fixture = accountFixture(
            OkHttpClient.Builder().addInterceptor { chain ->
                requestReachedServer = true
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody())
                    .build()
            }.build()
        )
        val secret = BrokerValue.Secret(
            SecretReference(CredentialHandle("persistent:account-token"))
        )
        val failure = brokerFailure {
            fixture.broker.execute(
                scope = fixture.scope,
                principal = PRINCIPAL,
                hook = ACCOUNT_HOOK,
                request = BrokeredHttpRequest(
                    method = "POST",
                    url = "$BASE_URL/items",
                    body = List(33) { index ->
                        BrokerValue.Concatenated(
                            listOf(BrokerValue.Literal("$index:"), secret)
                        )
                    },
                ),
            )
        }

        assertEquals(BrokerErrorCodes.InvalidRequest, failure.code)
        assertFalse(requestReachedServer)
    }

    @Test
    fun brokerRedactsManyLongSharedPrefixesInOneBoundedPass() = runBlocking {
        val responseBody = "a".repeat(720 * 1024)
        val fixture = accountFixture(
            OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(responseBody.toResponseBody())
                    .build()
            }.build()
        )
        val secret = BrokerValue.Secret(
            SecretReference(CredentialHandle("persistent:account-token"))
        )

        val response = withTimeout(5_000L) {
            fixture.broker.execute(
                scope = fixture.scope,
                principal = PRINCIPAL,
                hook = ACCOUNT_HOOK,
                request = BrokeredHttpRequest(
                    method = "POST",
                    url = "$BASE_URL/items",
                    body = List(31) { index ->
                        BrokerValue.Concatenated(
                            listOf(
                                BrokerValue.Literal("a".repeat(2_000 + index)),
                                secret,
                            )
                        )
                    },
                ),
            )
        }

        assertEquals(responseBody.length, response.body.length)
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
    fun authenticationJsonPointerTraversesArraysAndEscapedObjectKeys() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(
                    """
                    {
                      "accounts": [
                        {"id": "first-user"},
                        {"credentials": {"access/token": "array-token"}}
                      ],
                      "~meta": {"user/id": "escaped-user"}
                    }
                    """.trimIndent().toResponseBody()
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

        val response = HostNetworkBrokerImpl(client, store).authenticate(
            scope = scope,
            principal = PRINCIPAL,
            hook = ExtensionHookIds.SubscriptionProviderValidate,
            request = BrokerAuthenticationRequest(
                exchange = BrokerHttpExchange(method = "GET", url = "$BASE_URL/login"),
                primaryCredentialSource = ResponseValueSource.JsonPointer(
                    "/accounts/1/credentials/access~1token"
                ),
                opaqueContexts = listOf(
                    OpaqueContextCapture(
                        key = "first_user",
                        source = ResponseValueSource.JsonPointer("/accounts/0/id"),
                    ),
                    OpaqueContextCapture(
                        key = "escaped_user",
                        source = ResponseValueSource.JsonPointer("/~0meta/user~1id"),
                    ),
                ),
            ),
        )

        val authentication = store.consumeAuthenticationReceipt(
            scope = scope,
            principal = PRINCIPAL,
            receipt = checkNotNull(response.receipt),
        )
        assertEquals(
            "array-token",
            store.resolveCredential(
                scope = scope,
                principal = PRINCIPAL,
                hook = ExtensionHookIds.SubscriptionProviderValidate,
                handle = authentication.credentialHandle,
            ),
        )
        assertEquals("first-user", authentication.opaqueContexts["first_user"])
        assertEquals("escaped-user", authentication.opaqueContexts["escaped_user"])
    }

    @Test
    fun authenticationJsonPointerSupportsRootPrimitiveAndRejectsInvalidArrayIndices() =
        runBlocking {
            val responseBodies = ArrayDeque(
                listOf(
                    "\"root-token\"",
                    """{"accounts":["token"]}""",
                    """{"accounts":["token"]}""",
                    """{"accounts":["token"]}""",
                )
            )
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(responseBodies.removeFirst().toResponseBody())
                    .build()
            }.build()
            val vault = FakeCredentialVault()
            val registry = ActiveExtensionPrincipalRegistry().apply { activate(PRINCIPAL) }
            val store = scopeStore(vault, registry)
            val broker = HostNetworkBrokerImpl(client, store)

            val rootScope = store.mintAuthenticationScope(
                principal = PRINCIPAL,
                approvedBaseUrl = BASE_URL,
                transientCredentials = emptyMap(),
            )
            val rootResponse = broker.authenticate(
                scope = rootScope,
                principal = PRINCIPAL,
                hook = ExtensionHookIds.SubscriptionProviderValidate,
                request = BrokerAuthenticationRequest(
                    exchange = BrokerHttpExchange(method = "GET", url = "$BASE_URL/login"),
                    primaryCredentialSource = ResponseValueSource.JsonPointer(""),
                ),
            )
            assertTrue(rootResponse.receipt != null)

            listOf("/accounts/01", "/accounts/-", "/accounts/1").forEach { pointer ->
                val scope = store.mintAuthenticationScope(
                    principal = PRINCIPAL,
                    approvedBaseUrl = BASE_URL,
                    transientCredentials = emptyMap(),
                )
                val failure = brokerFailure {
                    broker.authenticate(
                        scope = scope,
                        principal = PRINCIPAL,
                        hook = ExtensionHookIds.SubscriptionProviderValidate,
                        request = BrokerAuthenticationRequest(
                            exchange = BrokerHttpExchange(
                                method = "GET",
                                url = "$BASE_URL/login",
                            ),
                            primaryCredentialSource = ResponseValueSource.JsonPointer(pointer),
                        ),
                    )
                }
                assertEquals(BrokerErrorCodes.InvalidRequest, failure.code)
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
        primaryCredential: String = "secret-token",
        userId: String = "opaque-user",
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
            secret = ProviderCredentialMaterial(
                primaryCredential = primaryCredential,
                opaqueContexts = mapOf("user_id" to userId),
            ).encode(),
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

    private fun nestedArrays(depth: Int, value: String): String =
        "[".repeat(depth) + value + "]".repeat(depth)

    private data class AccountFixture(
        val broker: HostNetworkBrokerImpl,
        val scope: BrokerScopeHandle,
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
