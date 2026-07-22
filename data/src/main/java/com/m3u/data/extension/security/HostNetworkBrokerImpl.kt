package com.m3u.data.extension.security

import com.m3u.data.api.ProviderOkhttpClient
import com.m3u.data.database.dao.ProviderDao
import com.m3u.extension.api.security.BrokerValue
import com.m3u.extension.api.security.BrokeredHttpRequest
import com.m3u.extension.api.security.BrokeredHttpResponse
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.api.security.HostNetworkBroker
import com.m3u.extension.api.security.SecretCaptureRule
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal class HostNetworkBrokerImpl @Inject constructor(
    @ProviderOkhttpClient private val client: OkHttpClient,
    private val providerDao: ProviderDao,
    private val credentialVault: CredentialVault,
) : HostNetworkBroker {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun execute(
        extensionId: String,
        accountId: String,
        request: BrokeredHttpRequest,
    ): BrokeredHttpResponse = withContext(Dispatchers.IO) {
        require(request.method.uppercase() in ALLOWED_METHODS) { "Unsupported HTTP method" }
        require(request.maximumResponseBytes in 1..MAX_RESPONSE_BYTES) {
            "Invalid extension response limit"
        }
        val account = providerDao.getAccount(accountId)
            ?: error("Provider account was not found")
        require(account.providerId == extensionId) {
            "Extension cannot access an account owned by another provider"
        }
        val allowedOrigins = setOf(normalizeOrigin(account.baseUrl))
        var url = request.url.toHttpUrlOrThrow()
        require(url.origin in allowedOrigins) { "Extension request origin is not approved" }
        val requestBody = buildString {
            request.body.forEach { value -> append(resolve(value)) }
        }
            .takeIf(String::isNotEmpty)
            ?.toRequestBody(request.headers["Content-Type"].literalOrNull()?.toMediaTypeOrNull())
        var redirects = 0
        while (true) {
            val builder = Request.Builder().url(url)
            request.headers.forEach { (name, value) ->
                if (name.equals("Content-Length", ignoreCase = true) || name.equals("Host", ignoreCase = true)) return@forEach
                if (SENSITIVE_HEADERS.any { it.equals(name, ignoreCase = true) } && value is BrokerValue.Literal) {
                    error("Sensitive headers require a credential reference")
                }
                builder.header(name, resolve(value))
            }
            builder.method(request.method.uppercase(), requestBody)
            client.newBuilder().followRedirects(false).followSslRedirects(false).build()
                .newCall(builder.build()).execute().use { response ->
                    if (response.isRedirect) {
                        require(redirects++ < MAX_REDIRECTS) { "Too many extension redirects" }
                        url = response.header("Location")?.let(url::resolve)
                            ?: throw IOException("Redirect response has no location")
                        require(url.origin in allowedOrigins) { "Cross-origin extension redirect was rejected" }
                        continue
                    }
                    val body = response.body?.source()?.let { source ->
                        source.request(request.maximumResponseBytes.toLong() + 1)
                        val bytes = source.readByteArray(request.maximumResponseBytes.toLong() + 1)
                        require(bytes.size <= request.maximumResponseBytes) { "Extension response exceeds limit" }
                        bytes.decodeToString()
                    }.orEmpty()
                    val captured = capture(request.secretCapture, response.headers.toMultimap(), body)
                    val handle = captured?.let { (secret, targetHandle) ->
                        val existingHandle = targetHandle?.value
                            ?: providerDao.getCredential(accountId)?.credentialHandle
                        val encrypted = credentialVault.encrypt(accountId, secret, existingHandle)
                        providerDao.insertOrReplace(encrypted)
                        CredentialHandle(encrypted.credentialHandle)
                    }
                    val captureRule = request.secretCapture
                    return@withContext BrokeredHttpResponse(
                        statusCode = response.code,
                        headers = response.headers.toMap().filterKeys { name ->
                            captureRule !is SecretCaptureRule.ResponseHeader ||
                                !name.equals(captureRule.name, ignoreCase = true)
                        },
                        body = captured?.first?.let { secret -> body.replace(secret, "***") } ?: body,
                        capturedCredential = handle,
                    )
                }
        }
        error("Unreachable")
    }

    private suspend fun resolve(value: BrokerValue): String = when (value) {
        is BrokerValue.Literal -> value.value
        is BrokerValue.Secret -> {
            val credential = providerDao.getCredentialByHandle(value.reference.handle.value)
                ?: error("Credential handle was not found")
            credentialVault.decrypt(credential) ?: run {
                providerDao.deleteCredential(credential.accountId)
                providerDao.setRequiresReauthentication(credential.accountId, true)
                error("Credential must be entered again")
            }
        }
    }

    private fun capture(
        rule: SecretCaptureRule?,
        headers: Map<String, List<String>>,
        body: String,
    ): Pair<String, CredentialHandle?>? = when (rule) {
        null -> null
        is SecretCaptureRule.ResponseHeader -> {
            val value = headers.entries.firstOrNull { it.key.equals(rule.name, true) }?.value?.firstOrNull()
                ?: error("Credential response header was not found")
            value to rule.targetHandle
        }
        is SecretCaptureRule.JsonPointer -> json.parseToJsonElement(body)
            .atJsonPointer(rule.pointer)
            .jsonPrimitive.content to rule.targetHandle
    }

    private fun JsonElement.atJsonPointer(pointer: String): JsonElement {
        require(pointer.startsWith('/')) { "JSON pointer must be absolute" }
        return pointer.split('/').drop(1).fold(this) { current, segment ->
            current.jsonObject[segment.replace("~1", "/").replace("~0", "~")]
                ?: error("Credential JSON pointer was not found")
        }
    }

    private fun String.toHttpUrlOrThrow(): HttpUrl = toHttpUrl()
    private fun normalizeOrigin(value: String): String = value.toHttpUrlOrThrow().origin
    private val HttpUrl.origin: String get() = "$scheme://$host:$port"
    private fun BrokerValue?.literalOrNull(): String? = (this as? BrokerValue.Literal)?.value

    private companion object {
        val ALLOWED_METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD")
        val SENSITIVE_HEADERS = setOf("Authorization", "Cookie", "X-Emby-Token")
        const val MAX_REDIRECTS = 5
        const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
    }
}
