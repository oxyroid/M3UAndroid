package com.m3u.extension.api.security

import com.m3u.extension.api.ExtensionPayload
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class CredentialHandle(val value: String) {
    init {
        require(value.isNotBlank()) { "Credential handle must not be blank" }
    }
}

@Serializable
data class SecretReference(
    val handle: CredentialHandle,
)

@Serializable
sealed interface BrokerValue {
    @Serializable
    data class Literal(val value: String) : BrokerValue

    @Serializable
    data class Secret(val reference: SecretReference) : BrokerValue
}

@Serializable
data class BrokeredHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, BrokerValue> = emptyMap(),
    val body: List<BrokerValue> = emptyList(),
    val maximumResponseBytes: Int = 1_048_576,
    val secretCapture: SecretCaptureRule? = null,
) : ExtensionPayload

@Serializable
sealed interface SecretCaptureRule {
    @Serializable
    data class ResponseHeader(
        val name: String,
        val targetHandle: CredentialHandle? = null,
    ) : SecretCaptureRule

    @Serializable
    data class JsonPointer(
        val pointer: String,
        val targetHandle: CredentialHandle? = null,
    ) : SecretCaptureRule
}

@Serializable
data class BrokeredHttpResponse(
    val statusCode: Int,
    val headers: Map<String, String>,
    val body: String,
    val capturedCredential: CredentialHandle? = null,
) : ExtensionPayload

interface HostNetworkBroker {
    suspend fun execute(
        extensionId: String,
        accountId: String,
        request: BrokeredHttpRequest,
    ): BrokeredHttpResponse
}

@Serializable
data class BrokerInvocation(
    val accountId: String,
    val request: BrokeredHttpRequest,
)
