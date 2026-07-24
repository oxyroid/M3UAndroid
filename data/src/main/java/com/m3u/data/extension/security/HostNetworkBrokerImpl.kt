package com.m3u.data.extension.security

import com.m3u.data.api.ProviderOkhttpClient
import com.m3u.extension.api.Hook
import com.m3u.extension.api.security.BrokerAuthenticationRequest
import com.m3u.extension.api.security.BrokerAuthenticationResponse
import com.m3u.extension.api.security.BrokerErrorCode
import com.m3u.extension.api.security.BrokerErrorCodes
import com.m3u.extension.api.security.BrokerHttpExchange
import com.m3u.extension.api.security.BrokerResponseRedaction
import com.m3u.extension.api.security.BrokerScopeHandle
import com.m3u.extension.api.security.BrokerValue
import com.m3u.extension.api.security.BrokeredHttpRequest
import com.m3u.extension.api.security.BrokeredHttpResponse
import com.m3u.extension.api.security.ResponseValueSource
import com.m3u.extension.api.security.referencesCredential
import com.m3u.extension.transport.android.requireSafeExtensionJsonDepth
import java.io.IOException
import java.io.InterruptedIOException
import java.util.ArrayDeque
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
        val resolvedSecrets = SensitiveValueCollector()
        val resolutionBudget = BrokerValueResolutionBudget()
        var url = request.url.renderForHost(
            maximumOutputLength = MAX_URL_LENGTH,
            budget = resolutionBudget,
            resolveSecret = { invalidRequest() },
            resolveContext = { reference ->
                scopeStore.resolveContext(scope, principal, hook, reference)
            },
            observeSensitiveValue = resolvedSecrets::observe,
        ).toHttpUrlOrThrow()
        if (url.origin !in access.approvedOrigins) {
            throw ProviderBrokerException(
                BrokerErrorCodes.ScopeDenied,
                recoverable = false,
            )
        }
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
                        observeSensitiveValue = resolvedSecrets::observe,
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
                    observeSensitiveValue = resolvedSecrets::observe,
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
                    if (url.origin !in access.approvedOrigins) {
                        throw ProviderBrokerException(
                            BrokerErrorCodes.ScopeDenied,
                            recoverable = false,
                        )
                    }
                    continue
                }
                val body = response.body.source().let { source ->
                    val buffer = Buffer()
                    val responseLimit = minOf(
                        request.maximumResponseBytes,
                        MAX_SAFE_RESPONSE_BODY_BYTES,
                    )
                    val limit = responseLimit.toLong() + 1
                    while (buffer.size < limit) {
                        val read = source.read(buffer, minOf(8_192L, limit - buffer.size))
                        if (read == -1L) break
                    }
                    if (buffer.size > responseLimit) {
                        throw ProviderBrokerException(
                            BrokerErrorCodes.ResponseTooLarge,
                            recoverable = false,
                        )
                    }
                    buffer.readByteArray().decodeToString()
                }
                return BrokeredHttpResponse(
                    statusCode = response.code,
                    headers = safeResponseHeaders(response, resolvedSecrets.values),
                    body = redactResponseBody(body, resolvedSecrets.values),
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
        val resolvedSensitiveValues = SensitiveValueCollector()
        val resolutionBudget = BrokerValueResolutionBudget()
        var url = exchange.url.renderForHost(
            maximumOutputLength = MAX_URL_LENGTH,
            budget = resolutionBudget,
            resolveSecret = { invalidRequest() },
            resolveContext = { reference ->
                scopeStore.resolveContext(scope, principal, hook, reference)
            },
            observeSensitiveValue = resolvedSensitiveValues::observe,
        ).toHttpUrlOrThrow()
        if (url.origin !in access.approvedOrigins) {
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
                        observeSensitiveValue = resolvedSensitiveValues::observe,
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
                    observeSensitiveValue = resolvedSensitiveValues::observe,
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
                    if (url.origin !in access.approvedOrigins) {
                        throw ProviderBrokerException(
                            BrokerErrorCodes.ScopeDenied,
                            recoverable = false,
                        )
                    }
                    continue
                }
                val body = response.body.source().let { source ->
                    val buffer = Buffer()
                    val responseLimit = minOf(
                        exchange.maximumResponseBytes,
                        MAX_SAFE_RESPONSE_BODY_BYTES,
                    )
                    val limit = responseLimit.toLong() + 1
                    while (buffer.size < limit) {
                        val read = source.read(buffer, minOf(8_192L, limit - buffer.size))
                        if (read == -1L) break
                    }
                    if (buffer.size > responseLimit) {
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

        is ResponseValueSource.JsonPointer -> parseBoundedJson(body)
            .atJsonPointer(source.pointer)
            .let { value -> value as? JsonPrimitive ?: invalidRequest() }
            .content
    }.also { value ->
        require(value.isNotEmpty()) { "Authentication capture is empty" }
    }

    private suspend fun redactResponseBody(
        body: String,
        sensitiveValues: Set<String>,
    ): String {
        val patterns = sensitiveValues
            .asSequence()
            .filter(String::isNotEmpty)
            .sortedByDescending(String::length)
            .toList()
        val matcher = patterns
            .takeIf(List<String>::isNotEmpty)
            ?.let(::SensitivePatternMatcher)
        val jsonBody = body.removePrefix(UTF_8_BOM)
        val firstContentCharacter = jsonBody.firstOrNull { character -> !character.isWhitespace() }
        if (firstContentCharacter?.canStartJsonValue() != true) {
            return matcher?.redact(body) ?: body
        }
        requireSafeBrokerJsonDepth(jsonBody)
        val parsed = try {
            json.parseToJsonElement(jsonBody)
        } catch (_: Exception) {
            return matcher?.redact(body) ?: body
        } catch (failure: StackOverflowError) {
            invalidRequest(failure)
        }
        val redacted = parsed.redacted(
            matcher = matcher,
            budget = JsonRedactionBudget(),
        )
        if (!redacted.changed) return body
        return try {
            json.encodeToString(JsonElement.serializer(), redacted.element).also { encoded ->
                if (encoded.length > MAX_REDACTED_RESPONSE_CHARS) {
                    throw ProviderBrokerException(
                        BrokerErrorCodes.ResponseTooLarge,
                        recoverable = false,
                    )
                }
            }
        } catch (failure: StackOverflowError) {
            invalidRequest(failure)
        }
    }

    private suspend fun SensitivePatternMatcher.redact(value: String): String {
        val longestMatchAt = findLongestMatches(value)
        if (longestMatchAt.none { length -> length > 0 }) return value
        return buildString(minOf(value.length, MAX_REDACTED_RESPONSE_CHARS)) {
            var index = 0
            while (index < value.length) {
                if (index % REDACTION_CANCELLATION_CHECK_INTERVAL == 0) {
                    currentCoroutineContext().ensureActive()
                }
                val matchLength = longestMatchAt[index]
                if (matchLength == 0) {
                    append(value[index++])
                } else {
                    var matchEnd = index + matchLength
                    var overlappingStart = index + 1
                    while (overlappingStart < matchEnd) {
                        if (
                            overlappingStart % REDACTION_CANCELLATION_CHECK_INTERVAL == 0
                        ) {
                            currentCoroutineContext().ensureActive()
                        }
                        val overlappingLength = longestMatchAt[overlappingStart]
                        if (overlappingLength > 0) {
                            matchEnd = maxOf(
                                matchEnd,
                                overlappingStart + overlappingLength,
                            )
                        }
                        overlappingStart++
                    }
                    append(BrokerResponseRedaction.RedactedValue)
                    index = matchEnd
                }
                if (length > MAX_REDACTED_RESPONSE_CHARS) {
                    throw ProviderBrokerException(
                        BrokerErrorCodes.ResponseTooLarge,
                        recoverable = false,
                    )
                }
            }
        }
    }

    /**
     * Aho-Corasick matcher used to redact every resolved secret in one bounded pass.
     *
     * Recording only the longest match for each start position preserves the previous
     * longest-secret-first behavior without rescanning the response once per secret.
     */
    private class SensitivePatternMatcher(patterns: List<String>) {
        private val nodes = mutableListOf(Node())

        init {
            patterns.forEach(::insert)
            buildFailureLinks()
        }

        suspend fun findLongestMatches(input: String): IntArray {
            val longestMatchAt = IntArray(input.length)
            var state = 0
            input.forEachIndexed { index, character ->
                if (index % REDACTION_CANCELLATION_CHECK_INTERVAL == 0) {
                    currentCoroutineContext().ensureActive()
                }
                while (state != 0 && character !in nodes[state].transitions) {
                    state = nodes[state].failure
                }
                state = nodes[state].transitions[character] ?: 0
                nodes[state].outputLengths.forEach { length ->
                    val start = index - length + 1
                    if (start >= 0 && length > longestMatchAt[start]) {
                        longestMatchAt[start] = length
                    }
                }
            }
            return longestMatchAt
        }

        private fun insert(pattern: String) {
            var state = 0
            pattern.forEach { character ->
                state = nodes[state].transitions.getOrPut(character) {
                    nodes.add(Node())
                    nodes.lastIndex
                }
            }
            nodes[state].outputLengths += pattern.length
        }

        private fun buildFailureLinks() {
            val pending = ArrayDeque<Int>()
            nodes[0].transitions.values.forEach { state ->
                pending.addLast(state)
            }
            while (pending.isNotEmpty()) {
                val state = pending.removeFirst()
                nodes[state].transitions.forEach { (character, child) ->
                    var fallback = nodes[state].failure
                    while (fallback != 0 && character !in nodes[fallback].transitions) {
                        fallback = nodes[fallback].failure
                    }
                    nodes[child].failure = nodes[fallback].transitions[character]
                        ?.takeUnless { candidate -> candidate == child }
                        ?: 0
                    nodes[child].outputLengths +=
                        nodes[nodes[child].failure].outputLengths
                    pending.addLast(child)
                }
            }
        }

        private data class Node(
            val transitions: MutableMap<Char, Int> = mutableMapOf(),
            val outputLengths: MutableList<Int> = mutableListOf(),
            var failure: Int = 0,
        )
    }

    private fun safeResponseHeaders(
        response: Response,
        sensitiveValues: Set<String>,
    ): Map<String, String> {
        val safe = linkedMapOf<String, String>()
        var totalBytes = 0
        response.headers.forEach { (name, value) ->
            if (SAFE_RESPONSE_HEADERS.none { allowed -> allowed.equals(name, true) }) {
                return@forEach
            }
            if (
                value.length > MAX_RESPONSE_HEADER_VALUE_CHARS ||
                sensitiveValues.any { secret -> secret.isNotEmpty() && secret in value }
            ) {
                return@forEach
            }
            val entryBytes = name.encodeToByteArray().size + value.encodeToByteArray().size
            if (
                safe.size >= MAX_RESPONSE_HEADERS ||
                totalBytes + entryBytes > MAX_RESPONSE_HEADER_BYTES
            ) {
                return@forEach
            }
            safe[name] = value
            totalBytes += entryBytes
        }
        return safe
    }

    private fun JsonElement.atJsonPointer(pointer: String): JsonElement {
        require(
            pointer.length <= MAX_JSON_POINTER_LENGTH &&
                (pointer.isEmpty() || pointer.startsWith('/'))
        ) {
            "JSON pointer must be RFC 6901 and bounded"
        }
        return pointer.decodeJsonPointerSegments().fold(this) { current, segment ->
            when (current) {
                is JsonObject -> current[segment] ?: invalidRequest()
                is JsonArray -> current[segment.toJsonArrayIndex(current.size)]
                else -> invalidRequest()
            }
        }
    }

    private fun String.decodeJsonPointerSegments(): List<String> = split('/')
        .drop(1)
        .map { segment ->
            buildString(segment.length) {
                var index = 0
                while (index < segment.length) {
                    when (val character = segment[index++]) {
                        '~' -> {
                            if (index >= segment.length) invalidRequest()
                            append(
                                when (segment[index++]) {
                                    '0' -> '~'
                                    '1' -> '/'
                                    else -> invalidRequest()
                                }
                            )
                        }
                        else -> append(character)
                    }
                }
            }
        }

    private fun String.toJsonArrayIndex(arraySize: Int): Int {
        if (
            this == "-" ||
            isEmpty() ||
            (length > 1 && first() == '0') ||
            any { character -> character !in '0'..'9' }
        ) {
            invalidRequest()
        }
        val index = toIntOrNull() ?: invalidRequest()
        if (index !in 0 until arraySize) invalidRequest()
        return index
    }

    private fun invalidRequest(cause: Throwable? = null): Nothing =
        throw ProviderBrokerException(
            code = BrokerErrorCodes.InvalidRequest,
            recoverable = false,
            cause = cause,
        )

    private fun String.isSensitiveRequestHeader(): Boolean =
        SENSITIVE_HEADER_NAME_PARTS.any { keyword -> contains(keyword, ignoreCase = true) }

    private fun parseBoundedJson(body: String): JsonElement {
        val jsonBody = body.removePrefix(UTF_8_BOM)
        requireSafeBrokerJsonDepth(jsonBody)
        return try {
            json.parseToJsonElement(jsonBody)
        } catch (failure: StackOverflowError) {
            invalidRequest(failure)
        }
    }

    private fun requireSafeBrokerJsonDepth(body: String) {
        try {
            body.requireSafeExtensionJsonDepth(MAX_BROKER_JSON_DEPTH)
        } catch (failure: IllegalArgumentException) {
            throw ProviderBrokerException(
                BrokerErrorCodes.ResponseTooLarge,
                recoverable = false,
                cause = failure,
            )
        }
    }

    private suspend fun JsonElement.redacted(
        matcher: SensitivePatternMatcher?,
        budget: JsonRedactionBudget,
    ): RedactedJson {
        budget.visit()
        return when (this) {
            is JsonObject -> {
                var changed = false
                val values = linkedMapOf<String, JsonElement>()
                for ((key, value) in this) {
                    val redactedKey = matcher?.redact(key) ?: key
                    val redactedValue = if (key.isAuthenticationResponseField()) {
                        RedactedJson(
                            element = JsonPrimitive(BrokerResponseRedaction.RedactedValue),
                            changed = value !=
                                JsonPrimitive(BrokerResponseRedaction.RedactedValue),
                        )
                    } else {
                        value.redacted(matcher, budget)
                    }
                    if (redactedKey in values) {
                        invalidRequest()
                    }
                    values[redactedKey] = redactedValue.element
                    changed = changed ||
                        redactedKey != key ||
                        redactedValue.changed
                }
                RedactedJson(
                    element = if (changed) JsonObject(values) else this,
                    changed = changed,
                )
            }
            is JsonArray -> {
                var changed = false
                val values = ArrayList<JsonElement>(size)
                for (value in this) {
                    val redactedValue = value.redacted(matcher, budget)
                    values += redactedValue.element
                    changed = changed || redactedValue.changed
                }
                RedactedJson(
                    element = if (changed) JsonArray(values) else this,
                    changed = changed,
                )
            }
            is JsonPrimitive -> {
                val redactedValue = matcher?.redact(content) ?: content
                RedactedJson(
                    element = if (redactedValue != content) {
                        JsonPrimitive(redactedValue)
                    } else {
                        this
                    },
                    changed = redactedValue != content,
                )
            }
        }
    }

    private fun String.isAuthenticationResponseField(): Boolean =
        BrokerResponseRedaction.isAuthenticationField(this)

    private fun Char.canStartJsonValue(): Boolean =
        this == '{' ||
            this == '[' ||
            this == '"' ||
            this == '-' ||
            this in '0'..'9' ||
            this == 't' ||
            this == 'f' ||
            this == 'n'

    private fun String.toHttpUrlOrThrow(): HttpUrl = toHttpUrl()
    private val HttpUrl.origin: String
        get() {
            val canonicalHost = host.let { value -> if (':' in value) "[$value]" else value }
            return "$scheme://$canonicalHost:$port"
        }
    private fun BrokerValue?.literalOrNull(): String? = (this as? BrokerValue.Literal)?.value

    private class SensitiveValueCollector {
        private val collected = linkedSetOf<String>()
        private var totalBytes = 0

        val values: Set<String>
            get() = collected

        fun observe(value: String) {
            if (value.isEmpty() || value in collected) return
            require(value.length <= MAX_SENSITIVE_VALUE_CHARS) {
                "Resolved sensitive broker value exceeds limit"
            }
            val byteCount = value.encodeToByteArray().size
            require(
                collected.size < MAX_SENSITIVE_VALUES &&
                    totalBytes + byteCount <= MAX_SENSITIVE_VALUE_BYTES
            ) {
                "Broker request contains too many sensitive value variants"
            }
            collected += value
            totalBytes += byteCount
        }
    }

    private class JsonRedactionBudget {
        private var visitedNodes = 0

        suspend fun visit() {
            if (
                visitedNodes++ % REDACTION_CANCELLATION_CHECK_INTERVAL == 0
            ) {
                currentCoroutineContext().ensureActive()
            }
        }
    }

    private data class RedactedJson(
        val element: JsonElement,
        val changed: Boolean,
    )

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
        val HTTP_HEADER_NAME = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")
        const val MAX_REDIRECTS = 5
        const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
        const val MAX_SAFE_RESPONSE_BODY_BYTES = 720 * 1024
        const val MAX_REDACTED_RESPONSE_CHARS = MAX_SAFE_RESPONSE_BODY_BYTES * 3
        const val MAX_REQUEST_BODY_BYTES = 512 * 1024
        const val MAX_REQUEST_HEADERS = 64
        const val MAX_BODY_PARTS = 64
        const val MAX_HEADER_VALUE_LENGTH = 16 * 1024
        const val MAX_RESPONSE_HEADERS = 16
        const val MAX_RESPONSE_HEADER_VALUE_CHARS = 8 * 1024
        const val MAX_RESPONSE_HEADER_BYTES = 32 * 1024
        const val MAX_URL_LENGTH = 8 * 1024
        const val MAX_JSON_POINTER_LENGTH = 512
        const val MAX_BROKER_JSON_DEPTH = 64
        const val MAX_SENSITIVE_VALUES = 32
        const val MAX_SENSITIVE_VALUE_CHARS = 16 * 1024
        const val MAX_SENSITIVE_VALUE_BYTES = 64 * 1024
        const val REDACTION_CANCELLATION_CHECK_INTERVAL = 4 * 1024
        const val UTF_8_BOM = "\uFEFF"
        const val MAX_CONCURRENT_BROKER_REQUESTS = 8
        const val BROKER_REQUEST_TIMEOUT_MILLIS = 25_000L
    }
}
