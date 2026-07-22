package com.m3u.data.extension.security

import com.m3u.data.api.ProviderOkhttpClient
import com.m3u.extension.api.Hook
import com.m3u.extension.api.security.BrokerAuthenticationRequest
import com.m3u.extension.api.security.BrokerAuthenticationResponse
import com.m3u.extension.api.security.BrokerErrorCode
import com.m3u.extension.api.security.BrokerErrorCodes
import com.m3u.extension.api.security.BrokerHttpExchange
import com.m3u.extension.api.security.BrokerScopeHandle
import com.m3u.extension.api.security.BrokerValue
import com.m3u.extension.api.security.BrokeredHttpRequest
import com.m3u.extension.api.security.BrokeredHttpResponse
import com.m3u.extension.api.security.ResponseValueSource
import com.m3u.extension.api.security.referencesCredential
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer

internal interface ProviderHostNetworkBroker {
    suspend fun execute(
        scope: BrokerScopeHandle,
        principal: ExtensionPrincipal,
        hook: Hook,
        request: BrokeredHttpRequest,
    ): BrokeredHttpResponse

    suspend fun authenticate(
        scope: BrokerScopeHandle,
        principal: ExtensionPrincipal,
        hook: Hook,
        request: BrokerAuthenticationRequest,
    ): BrokerAuthenticationResponse = throw ProviderBrokerException(
        code = BrokerErrorCodes.Internal,
        recoverable = true,
    )
}

internal class ProviderBrokerException(
    val code: BrokerErrorCode,
    val recoverable: Boolean,
    cause: Throwable? = null,
) : Exception(null, cause, false, false)

internal class HostNetworkBrokerImpl @Inject constructor(
    @param:ProviderOkhttpClient private val client: OkHttpClient,
    private val scopeStore: ProviderBrokerScopeStore,
) : ProviderHostNetworkBroker {
    private val json = Json { ignoreUnknownKeys = true }
    private val brokerClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(BROKER_REQUEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        .build()
    private val requestPermits = Semaphore(MAX_CONCURRENT_BROKER_REQUESTS)

    override suspend fun execute(
        scope: BrokerScopeHandle,
        principal: ExtensionPrincipal,
        hook: Hook,
        request: BrokeredHttpRequest,
    ): BrokeredHttpResponse = try {
        withTimeoutOrNull(BROKER_REQUEST_TIMEOUT_MILLIS) {
            requestPermits.withPermit {
                executeWithinPermit(scope, principal, hook, request)
            }
        } ?: throw ProviderBrokerException(BrokerErrorCodes.Timeout, recoverable = true)
    } catch (failure: ProviderBrokerException) {
        throw failure
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: SecurityException) {
        throw ProviderBrokerException(BrokerErrorCodes.ScopeDenied, recoverable = false, failure)
    } catch (failure: IllegalArgumentException) {
        throw ProviderBrokerException(BrokerErrorCodes.InvalidRequest, recoverable = false, failure)
    } catch (failure: InterruptedIOException) {
        currentCoroutineContext().ensureActive()
        throw ProviderBrokerException(BrokerErrorCodes.Timeout, recoverable = true, failure)
    } catch (failure: IOException) {
        currentCoroutineContext().ensureActive()
        throw ProviderBrokerException(BrokerErrorCodes.NetworkFailed, recoverable = true, failure)
    } catch (failure: Exception) {
        throw ProviderBrokerException(BrokerErrorCodes.Internal, recoverable = true, failure)
    }

    override suspend fun authenticate(
        scope: BrokerScopeHandle,
        principal: ExtensionPrincipal,
        hook: Hook,
        request: BrokerAuthenticationRequest,
    ): BrokerAuthenticationResponse = try {
        withTimeoutOrNull(BROKER_REQUEST_TIMEOUT_MILLIS) {
            requestPermits.withPermit {
                authenticateWithinPermit(scope, principal, hook, request)
            }
        } ?: throw ProviderBrokerException(BrokerErrorCodes.Timeout, recoverable = true)
    } catch (failure: ProviderBrokerException) {
        throw failure
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: SecurityException) {
        throw ProviderBrokerException(BrokerErrorCodes.ScopeDenied, recoverable = false, failure)
    } catch (failure: IllegalArgumentException) {
        throw ProviderBrokerException(BrokerErrorCodes.InvalidRequest, recoverable = false, failure)
    } catch (failure: InterruptedIOException) {
        currentCoroutineContext().ensureActive()
        throw ProviderBrokerException(BrokerErrorCodes.Timeout, recoverable = true, failure)
    } catch (failure: IOException) {
        currentCoroutineContext().ensureActive()
        throw ProviderBrokerException(BrokerErrorCodes.NetworkFailed, recoverable = true, failure)
    } catch (failure: Exception) {
        throw ProviderBrokerException(BrokerErrorCodes.Internal, recoverable = true, failure)
    }

    private suspend fun executeWithinPermit(
        scope: BrokerScopeHandle,
        principal: ExtensionPrincipal,
        hook: Hook,
        request: BrokeredHttpRequest,
    ): BrokeredHttpResponse {
        val access = scopeStore.authorize(scope, principal, hook)
        if (access.kind == ProviderBrokerScopeKind.AUTHENTICATION) {
            invalidRequest()
        }
        val method = request.method.uppercase()
        require(method in ALLOWED_METHODS) { "Unsupported HTTP method" }
        require(request.maximumResponseBytes in 1..MAX_RESPONSE_BYTES) {
            "Invalid extension response limit"
        }
        require(request.headers.size <= MAX_REQUEST_HEADERS) {
            "Extension request contains too many headers"
        }
        require(request.body.size <= MAX_BODY_PARTS) {
            "Extension request body has too many parts"
        }
        if (request.url.referencesCredential()) invalidRequest()
        var url = request.url.renderForHost(
            maximumOutputLength = MAX_URL_LENGTH,
            budget = BrokerValueResolutionBudget(),
            resolveSecret = { invalidRequest() },
            resolveContext = { reference ->
                scopeStore.resolveContext(scope, principal, hook, reference)
            },
        ).toHttpUrlOrThrow()
        if (url.origin != access.approvedOrigin) {
            throw ProviderBrokerException(
                BrokerErrorCodes.ScopeDenied,
                recoverable = false,
            )
        }
        val resolvedSecrets = linkedSetOf<String>()
        val resolutionBudget = BrokerValueResolutionBudget()
        val requestBodyValue = buildString {
            request.body.forEach { value ->
                append(
                    value.renderForHost(
                        maximumOutputLength = MAX_REQUEST_BODY_BYTES,
                        budget = resolutionBudget,
                        resolveSecret = { reference ->
                            scopeStore.resolveCredential(
                                scope = scope,
                                principal = principal,
                                hook = hook,
                                handle = reference.handle,
                            )
                        },
                        resolveContext = { reference ->
                            scopeStore.resolveContext(scope, principal, hook, reference)
                        },
                        observeSensitiveValue = resolvedSecrets::add,
                    )
                )
                require(length <= MAX_REQUEST_BODY_BYTES) {
                    "Extension request body exceeds limit"
                }
            }
        }
        require(method !in BODYLESS_METHODS || requestBodyValue.isEmpty()) {
            "$method requests cannot contain a body"
        }
        val requestMediaType = request.headers["Content-Type"]
            .literalOrNull()
            ?.toMediaTypeOrNull()
        val requestBody = when {
            requestBodyValue.isNotEmpty() -> requestBodyValue.toRequestBody(requestMediaType)
            method in BODY_REQUIRED_METHODS -> "".toRequestBody(requestMediaType)
            else -> null
        }
        var redirects = 0
        while (true) {
            val builder = Request.Builder().url(url)
            request.headers.forEach { (name, value) ->
                require(name.matches(HTTP_HEADER_NAME)) { "Invalid extension request header name" }
                if (name.equals("Content-Length", true) || name.equals("Host", true)) {
                    return@forEach
                }
                if (name.isSensitiveRequestHeader() && !value.referencesCredential()) {
                    invalidRequest()
                }
                val resolved = value.renderForHost(
                    maximumOutputLength = MAX_HEADER_VALUE_LENGTH,
                    budget = resolutionBudget,
                    resolveSecret = { reference ->
                        scopeStore.resolveCredential(
                            scope = scope,
                            principal = principal,
                            hook = hook,
                            handle = reference.handle,
                        )
                    },
                    resolveContext = { reference ->
                        scopeStore.resolveContext(scope, principal, hook, reference)
                    },
                    observeSensitiveValue = resolvedSecrets::add,
                )
                require(
                    resolved.length <= MAX_HEADER_VALUE_LENGTH &&
                        '\r' !in resolved &&
                        '\n' !in resolved
                ) { "Invalid extension request header value" }
                builder.header(name, resolved)
            }
            builder.method(method, requestBody)
            val call = brokerClient.newCall(builder.build())
            call.awaitResponse().use { response ->
                if (response.isRedirect) {
                    require(redirects++ < MAX_REDIRECTS) { "Too many extension redirects" }
                    url = response.header("Location")?.let(url::resolve)
                        ?: throw IOException("Redirect response has no location")
                    if (url.origin != access.approvedOrigin) {
                        throw ProviderBrokerException(
                            BrokerErrorCodes.ScopeDenied,
                            recoverable = false,
                        )
                    }
                    continue
                }
                val body = response.body.source().let { source ->
                    val buffer = Buffer()
                    val limit = request.maximumResponseBytes.toLong() + 1
                    while (buffer.size < limit) {
                        val read = source.read(buffer, minOf(8_192L, limit - buffer.size))
                        if (read == -1L) break
                    }
                    if (buffer.size > request.maximumResponseBytes) {
                        throw ProviderBrokerException(
                            BrokerErrorCodes.ResponseTooLarge,
                            recoverable = false,
                        )
                    }
                    buffer.readByteArray().decodeToString()
                }
                return BrokeredHttpResponse(
                    statusCode = response.code,
                    headers = response.headers.toMap().filter { (name, value) ->
                        SAFE_RESPONSE_HEADERS.any { it.equals(name, true) } &&
                            resolvedSecrets.none { secret -> secret.isNotEmpty() && secret in value }
                    },
                    body = redactResponseBody(body, resolvedSecrets),
                )
            }
        }
    }

    private suspend fun authenticateWithinPermit(
        scope: BrokerScopeHandle,
        principal: ExtensionPrincipal,
        hook: Hook,
        request: BrokerAuthenticationRequest,
    ): BrokerAuthenticationResponse {
        val access = scopeStore.authorize(scope, principal, hook)
        if (access.kind != ProviderBrokerScopeKind.AUTHENTICATION) {
            throw ProviderBrokerException(BrokerErrorCodes.ScopeDenied, recoverable = false)
        }
        val exchange = request.exchange
        val method = exchange.method.uppercase()
        require(method in ALLOWED_METHODS) { "Unsupported HTTP method" }
        require(exchange.maximumResponseBytes in 1..MAX_RESPONSE_BYTES) {
            "Invalid extension response limit"
        }
        require(exchange.headers.size <= MAX_REQUEST_HEADERS) {
            "Extension request contains too many headers"
        }
        require(exchange.body.size <= MAX_BODY_PARTS) {
            "Extension request body has too many parts"
        }
        if (exchange.url.referencesCredential()) invalidRequest()
        val resolvedSensitiveValues = linkedSetOf<String>()
        val resolutionBudget = BrokerValueResolutionBudget()
        var url = exchange.url.renderForHost(
            maximumOutputLength = MAX_URL_LENGTH,
            budget = resolutionBudget,
            resolveSecret = { invalidRequest() },
            resolveContext = { reference ->
                scopeStore.resolveContext(scope, principal, hook, reference)
            },
            observeSensitiveValue = resolvedSensitiveValues::add,
        ).toHttpUrlOrThrow()
        if (url.origin != access.approvedOrigin) {
            throw ProviderBrokerException(BrokerErrorCodes.ScopeDenied, recoverable = false)
        }
        val requestBodyValue = buildString {
            exchange.body.forEach { value ->
                append(
                    value.renderForHost(
                        maximumOutputLength = MAX_REQUEST_BODY_BYTES,
                        budget = resolutionBudget,
                        resolveSecret = { reference ->
                            scopeStore.resolveCredential(
                                scope = scope,
                                principal = principal,
                                hook = hook,
                                handle = reference.handle,
                            )
                        },
                        resolveContext = { reference ->
                            scopeStore.resolveContext(scope, principal, hook, reference)
                        },
                        observeSensitiveValue = resolvedSensitiveValues::add,
                    )
                )
                require(length <= MAX_REQUEST_BODY_BYTES) {
                    "Extension request body exceeds limit"
                }
            }
        }
        require(method !in BODYLESS_METHODS || requestBodyValue.isEmpty()) {
            "$method requests cannot contain a body"
        }
        val requestMediaType = exchange.headers["Content-Type"]
            .literalOrNull()
            ?.toMediaTypeOrNull()
        val requestBody = when {
            requestBodyValue.isNotEmpty() -> requestBodyValue.toRequestBody(requestMediaType)
            method in BODY_REQUIRED_METHODS -> "".toRequestBody(requestMediaType)
            else -> null
        }
        var redirects = 0
        while (true) {
            val builder = Request.Builder().url(url)
            exchange.headers.forEach { (name, value) ->
                require(name.matches(HTTP_HEADER_NAME)) {
                    "Invalid extension request header name"
                }
                if (name.equals("Content-Length", true) || name.equals("Host", true)) {
                    return@forEach
                }
                if (name.isSensitiveRequestHeader() && !value.referencesCredential()) {
                    invalidRequest()
                }
                val resolved = value.renderForHost(
                    maximumOutputLength = MAX_HEADER_VALUE_LENGTH,
                    budget = resolutionBudget,
                    resolveSecret = { reference ->
                        scopeStore.resolveCredential(
                            scope = scope,
                            principal = principal,
                            hook = hook,
                            handle = reference.handle,
                        )
                    },
                    resolveContext = { reference ->
                        scopeStore.resolveContext(scope, principal, hook, reference)
                    },
                    observeSensitiveValue = resolvedSensitiveValues::add,
                )
                require(
                    resolved.length <= MAX_HEADER_VALUE_LENGTH &&
                        '\r' !in resolved &&
                        '\n' !in resolved
                ) { "Invalid extension request header value" }
                builder.header(name, resolved)
            }
            builder.method(method, requestBody)
            brokerClient.newCall(builder.build()).awaitResponse().use { response ->
                if (response.isRedirect) {
                    require(redirects++ < MAX_REDIRECTS) {
                        "Too many extension redirects"
                    }
                    url = response.header("Location")?.let(url::resolve)
                        ?: throw IOException("Redirect response has no location")
                    if (url.origin != access.approvedOrigin) {
                        throw ProviderBrokerException(
                            BrokerErrorCodes.ScopeDenied,
                            recoverable = false,
                        )
                    }
                    continue
                }
                val body = response.body.source().let { source ->
                    val buffer = Buffer()
                    val limit = exchange.maximumResponseBytes.toLong() + 1
                    while (buffer.size < limit) {
                        val read = source.read(buffer, minOf(8_192L, limit - buffer.size))
                        if (read == -1L) break
                    }
                    if (buffer.size > exchange.maximumResponseBytes) {
                        throw ProviderBrokerException(
                            BrokerErrorCodes.ResponseTooLarge,
                            recoverable = false,
                        )
                    }
                    buffer.readByteArray().decodeToString()
                }
                if (!response.isSuccessful) {
                    return BrokerAuthenticationResponse(statusCode = response.code)
                }
                val headers = response.headers.toMultimap()
                val primaryCredential = extractAuthenticationValue(
                    source = request.primaryCredentialSource,
                    headers = headers,
                    body = body,
                )
                val contexts = request.opaqueContexts.associate { capture ->
                    capture.key to extractAuthenticationValue(
                        source = capture.source,
                        headers = headers,
                        body = body,
                    )
                }
                val receipt = try {
                    scopeStore.recordAuthentication(
                        scope = scope,
                        principal = principal,
                        hook = hook,
                        primaryCredential = primaryCredential,
                        opaqueContexts = contexts,
                    )
                } catch (failure: IllegalStateException) {
                    invalidRequest(failure)
                }
                return BrokerAuthenticationResponse(
                    statusCode = response.code,
                    receipt = receipt,
                )
            }
        }
    }

    private suspend fun Call.awaitResponse(): Response =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancel() }
            enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.failure(e))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { _, unconsumedResponse, _ ->
                        unconsumedResponse.close()
                    }
                }
            })
        }

    private fun extractAuthenticationValue(
        source: ResponseValueSource,
        headers: Map<String, List<String>>,
        body: String,
    ): String = when (source) {
        is ResponseValueSource.Header -> headers.entries
            .firstOrNull { (name, _) -> name.equals(source.name, ignoreCase = true) }
            ?.value
            ?.singleOrNull()
            ?: invalidRequest()

        is ResponseValueSource.JsonPointer -> json.parseToJsonElement(body)
            .atJsonPointer(source.pointer)
            .let { value -> value as? JsonPrimitive ?: invalidRequest() }
            .content
    }.also { value ->
        require(value.isNotEmpty()) { "Authentication capture is empty" }
    }

    private fun redactResponseBody(body: String, sensitiveValues: Set<String>): String {
        val exactRedacted = sensitiveValues
            .filter(String::isNotEmpty)
            .fold(body) { current, secret -> current.replace(secret, "***") }
        return redactSensitiveJson(exactRedacted)
    }

    private fun JsonElement.atJsonPointer(pointer: String): JsonElement {
        require(pointer.startsWith('/') && pointer.length <= MAX_JSON_POINTER_LENGTH) {
            "JSON pointer must be absolute and bounded"
        }
        return pointer.split('/').drop(1).fold(this) { current, segment ->
            current.jsonObject[segment.replace("~1", "/").replace("~0", "~")]
                ?: invalidRequest()
        }
    }

    private fun invalidRequest(cause: Throwable? = null): Nothing =
        throw ProviderBrokerException(
            code = BrokerErrorCodes.InvalidRequest,
            recoverable = false,
            cause = cause,
        )

    private fun String.isSensitiveRequestHeader(): Boolean =
        SENSITIVE_HEADER_NAME_PARTS.any { keyword -> contains(keyword, ignoreCase = true) }

    private fun redactSensitiveJson(body: String): String = runCatching {
        json.encodeToString(JsonElement.serializer(), json.parseToJsonElement(body).redacted())
    }.getOrDefault(body)

    private fun JsonElement.redacted(): JsonElement = when (this) {
        is JsonObject -> JsonObject(mapValues { (key, value) ->
            if (SENSITIVE_JSON_KEYS.any { key.contains(it, ignoreCase = true) }) {
                JsonPrimitive("***")
            } else {
                value.redacted()
            }
        })
        is JsonArray -> JsonArray(map { element -> element.redacted() })
        else -> this
    }

    private fun String.toHttpUrlOrThrow(): HttpUrl = toHttpUrl()
    private val HttpUrl.origin: String
        get() {
            val canonicalHost = host.let { value -> if (':' in value) "[$value]" else value }
            return "$scheme://$canonicalHost:$port"
        }
    private fun BrokerValue?.literalOrNull(): String? = (this as? BrokerValue.Literal)?.value

    private companion object {
        val ALLOWED_METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD")
        val BODYLESS_METHODS = setOf("GET", "HEAD")
        val BODY_REQUIRED_METHODS = setOf("POST", "PUT", "PATCH")
        val SENSITIVE_HEADER_NAME_PARTS = setOf(
            "authorization",
            "cookie",
            "token",
            "api-key",
            "apikey",
            "secret",
            "credential",
        )
        val SAFE_RESPONSE_HEADERS = setOf(
            "Cache-Control",
            "Content-Language",
            "Content-Type",
            "ETag",
            "Expires",
            "Last-Modified",
            "Retry-After",
            "X-RateLimit-Limit",
            "X-RateLimit-Remaining",
            "X-RateLimit-Reset",
        )
        val SENSITIVE_JSON_KEYS = setOf(
            "token",
            "password",
            "secret",
            "authorization",
            "credential",
        )
        val HTTP_HEADER_NAME = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")
        const val MAX_REDIRECTS = 5
        const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
        const val MAX_REQUEST_BODY_BYTES = 512 * 1024
        const val MAX_REQUEST_HEADERS = 64
        const val MAX_BODY_PARTS = 64
        const val MAX_HEADER_VALUE_LENGTH = 16 * 1024
        const val MAX_URL_LENGTH = 8 * 1024
        const val MAX_JSON_POINTER_LENGTH = 512
        const val MAX_CONCURRENT_BROKER_REQUESTS = 8
        const val BROKER_REQUEST_TIMEOUT_MILLIS = 25_000L
    }
}
