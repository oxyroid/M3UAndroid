package com.m3u.extension.api.security

import com.m3u.extension.api.ExtensionPayload
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class CredentialHandle(val value: String) {
    init {
        require(value.isNotBlank()) { "Credential handle must not be blank" }
    }
}

@Serializable
@JvmInline
value class BrokerScopeHandle(val value: String) {
    init {
        require(value.isNotBlank()) { "Broker scope handle must not be blank" }
    }
}

object BrokerProtocolVersions {
    const val Current: Int = 4
    val Supported: Set<Int> = setOf(Current)

    fun negotiate(peerSupported: Set<Int>): Int? =
        Supported.intersect(peerSupported).maxOrNull()
}

@Serializable
data class SecretReference(
    val handle: CredentialHandle,
)

@Serializable
sealed interface BrokerValue {
    @Serializable
    @SerialName("literal")
    data class Literal(val value: String) : BrokerValue

    @Serializable
    @SerialName("secret")
    data class Secret(val reference: SecretReference) : BrokerValue

    @Serializable
    @SerialName("context")
    data class Context(val reference: ContextReference) : BrokerValue

    /** Concatenates values after the host has resolved their credential references. */
    @Serializable
    @SerialName("concatenated")
    data class Concatenated(val parts: List<BrokerValue>) : BrokerValue {
        init {
            require(parts.isNotEmpty()) { "Concatenated broker value must not be empty" }
            require(parts.size <= 32) { "Concatenated broker value has too many parts" }
        }
    }

    /** Applies an encoding in the host process, after credential resolution. */
    @Serializable
    @SerialName("encoded")
    data class Encoded(
        val value: BrokerValue,
        val encoding: BrokerValueEncoding,
    ) : BrokerValue
}

@Serializable
enum class BrokerValueEncoding {
    /** Produces a complete JSON string value, including its surrounding quotes. */
    @SerialName("json_string")
    JsonString,

    /** Encodes one application/x-www-form-urlencoded field name or value. */
    @SerialName("form_url_component")
    FormUrlComponent,

    /** Produces standard RFC 4648 Base64 without line wrapping. */
    @SerialName("base64")
    Base64,
}

fun BrokerValue.referencesCredential(): Boolean {
    val pending = mutableListOf(this)
    var index = 0
    while (index < pending.size) {
        require(pending.size <= 256) { "Broker value contains too many nested values" }
        when (val value = pending[index++]) {
            is BrokerValue.Literal -> Unit
            is BrokerValue.Secret -> return true
            is BrokerValue.Context -> Unit
            is BrokerValue.Concatenated -> pending += value.parts
            is BrokerValue.Encoded -> pending += value.value
        }
    }
    return false
}

fun BrokerValue.referencesOpaqueContext(): Boolean {
    val pending = mutableListOf(this)
    var index = 0
    while (index < pending.size) {
        require(pending.size <= 256) { "Broker value contains too many nested values" }
        when (val value = pending[index++]) {
            is BrokerValue.Literal -> Unit
            is BrokerValue.Secret -> Unit
            is BrokerValue.Context -> return true
            is BrokerValue.Concatenated -> pending += value.parts
            is BrokerValue.Encoded -> pending += value.value
        }
    }
    return false
}

@Serializable
data class BrokerHttpExchange(
    val method: String,
    val url: BrokerValue,
    val headers: Map<String, BrokerValue> = emptyMap(),
    val body: List<BrokerValue> = emptyList(),
    val maximumResponseBytes: Int = 1_048_576,
) {
    constructor(
        method: String,
        url: String,
        headers: Map<String, BrokerValue> = emptyMap(),
        body: List<BrokerValue> = emptyList(),
        maximumResponseBytes: Int = 1_048_576,
    ) : this(
        method = method,
        url = BrokerValue.Literal(url),
        headers = headers,
        body = body,
        maximumResponseBytes = maximumResponseBytes,
    )
}

@Serializable
data class BrokeredHttpRequest(
    val method: String,
    val url: BrokerValue,
    val headers: Map<String, BrokerValue> = emptyMap(),
    val body: List<BrokerValue> = emptyList(),
    val maximumResponseBytes: Int = 1_048_576,
) : ExtensionPayload {
    constructor(
        method: String,
        url: String,
        headers: Map<String, BrokerValue> = emptyMap(),
        body: List<BrokerValue> = emptyList(),
        maximumResponseBytes: Int = 1_048_576,
    ) : this(
        method = method,
        url = BrokerValue.Literal(url),
        headers = headers,
        body = body,
        maximumResponseBytes = maximumResponseBytes,
    )
}

@Serializable
data class BrokeredHttpResponse(
    val statusCode: Int,
    val headers: Map<String, String>,
    val body: String,
) : ExtensionPayload

@Serializable
sealed interface ResponseValueSource {
    @Serializable
    @SerialName("header")
    data class Header(val name: String) : ResponseValueSource {
        init {
            require(name.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+"))) {
                "Invalid authentication response header name"
            }
            require(name.length <= 128) { "Authentication response header name is too long" }
        }
    }

    @Serializable
    @SerialName("json_pointer")
    data class JsonPointer(val pointer: String) : ResponseValueSource {
        init {
            require(pointer.startsWith('/') && pointer.length <= 512) {
                "Authentication response JSON pointer must be absolute and bounded"
            }
        }
    }
}

@Serializable
data class OpaqueContextCapture(
    val key: String,
    val source: ResponseValueSource,
) {
    init {
        require(key.matches(Regex("[a-z][a-z0-9._-]*"))) {
            "Invalid opaque context key"
        }
        require(key.length <= 64) { "Opaque context key is too long" }
    }
}

@Serializable
data class BrokerAuthenticationRequest(
    val exchange: BrokerHttpExchange,
    val primaryCredentialSource: ResponseValueSource,
    val opaqueContexts: List<OpaqueContextCapture> = emptyList(),
) {
    init {
        require(opaqueContexts.size <= 16) { "Authentication captures too many contexts" }
        require(opaqueContexts.map(OpaqueContextCapture::key).distinct().size == opaqueContexts.size) {
            "Authentication context keys must be unique"
        }
        val sources = listOf(primaryCredentialSource) + opaqueContexts.map(OpaqueContextCapture::source)
        require(sources.indices.none { left ->
            ((left + 1) until sources.size).any { right ->
                sources[left].overlaps(sources[right])
            }
        }) { "Authentication response sources must be distinct and non-overlapping" }
    }
}

@Serializable
@JvmInline
value class ProviderAuthenticationReceipt(val value: String) {
    init {
        require(value.isNotBlank()) { "Provider authentication receipt must not be blank" }
        require(value.length <= 256) { "Provider authentication receipt is too long" }
    }
}

@Serializable
@JvmInline
value class ContextReference(val key: String) {
    init {
        require(key.matches(Regex("[a-z][a-z0-9._-]*"))) {
            "Invalid opaque context reference"
        }
        require(key.length <= 64) { "Opaque context reference is too long" }
    }
}

@Serializable
data class BrokerAuthenticationResponse(
    val statusCode: Int,
    val receipt: ProviderAuthenticationReceipt? = null,
) {
    init {
        require(statusCode in 100..599) { "Invalid authentication HTTP status" }
        require((statusCode in 200..299) == (receipt != null)) {
            "Successful authentication requires a receipt and failures must not return one"
        }
    }
}

@Serializable
sealed interface BrokerOperation {
    @Serializable
    @SerialName("http")
    data class Http(val request: BrokeredHttpRequest) : BrokerOperation

    @Serializable
    @SerialName("authenticate")
    data class Authenticate(val request: BrokerAuthenticationRequest) : BrokerOperation
}

@Serializable
sealed interface BrokerOperationResult {
    @Serializable
    @SerialName("http")
    data class Http(val response: BrokeredHttpResponse) : BrokerOperationResult

    @Serializable
    @SerialName("authentication")
    data class Authentication(
        val response: BrokerAuthenticationResponse,
    ) : BrokerOperationResult
}

@Serializable
@JvmInline
value class BrokerErrorCode(val value: String) {
    init {
        require(value.matches(Regex("[a-z][a-z0-9_]*"))) {
            "Invalid broker error code: $value"
        }
    }
}

object BrokerErrorCodes {
    val InvalidRequest = BrokerErrorCode("invalid_request")
    val CapabilityDenied = BrokerErrorCode("capability_denied")
    val ScopeDenied = BrokerErrorCode("scope_denied")
    val Timeout = BrokerErrorCode("timeout")
    val Cancelled = BrokerErrorCode("cancelled")
    val NetworkFailed = BrokerErrorCode("network_failed")
    val ResponseTooLarge = BrokerErrorCode("response_too_large")
    val Internal = BrokerErrorCode("internal")
}

@Serializable
data class BrokerInvocationError(
    val code: BrokerErrorCode,
    val recoverable: Boolean,
    val message: String,
) {
    init {
        require(message.isNotBlank()) { "Broker error message must not be blank" }
        require(message.length <= 256) { "Broker error message exceeds the protocol limit" }
    }
}

@Serializable
sealed interface BrokerInvocationResult {
    @Serializable
    @SerialName("success")
    data class Success(
        val result: BrokerOperationResult,
    ) : BrokerInvocationResult

    @Serializable
    @SerialName("failure")
    data class Failure(
        val error: BrokerInvocationError,
    ) : BrokerInvocationResult
}

@Serializable
data class BrokerInvocation(
    val brokerProtocolVersion: Int,
    val operation: BrokerOperation,
) {
    init {
        require(brokerProtocolVersion in BrokerProtocolVersions.Supported) {
            "Unsupported broker protocol version: $brokerProtocolVersion"
        }
    }
}

private fun ResponseValueSource.overlaps(other: ResponseValueSource): Boolean = when {
    this is ResponseValueSource.Header && other is ResponseValueSource.Header ->
        name.equals(other.name, ignoreCase = true)
    this is ResponseValueSource.JsonPointer && other is ResponseValueSource.JsonPointer -> {
        val left = pointer.decodedPointerSegments()
        val right = other.pointer.decodedPointerSegments()
        left.isPrefixOf(right) || right.isPrefixOf(left)
    }
    else -> false
}

private fun String.decodedPointerSegments(): List<String> = split('/')
    .drop(1)
    .map { segment -> segment.replace("~1", "/").replace("~0", "~") }

private fun List<String>.isPrefixOf(other: List<String>): Boolean =
    size <= other.size && indices.all { index -> this[index] == other[index] }
