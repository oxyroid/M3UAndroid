package com.m3u.data.extension.security

import android.util.Base64
import com.m3u.extension.api.security.BrokerValue
import com.m3u.extension.api.security.BrokerValueEncoding
import com.m3u.extension.api.security.ContextReference
import com.m3u.extension.api.security.SecretReference
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

internal class BrokerValueResolutionBudget(
    private val maximumNodes: Int = 256,
    private val maximumDepth: Int = 8,
) {
    private var consumedNodes: Int = 0

    fun consume(depth: Int) {
        require(depth <= maximumDepth) { "Broker value nesting is too deep" }
        require(++consumedNodes <= maximumNodes) { "Broker value contains too many nodes" }
    }
}

internal fun BrokerValue.renderForHost(
    maximumOutputLength: Int,
    budget: BrokerValueResolutionBudget = BrokerValueResolutionBudget(),
    resolveSecret: (SecretReference) -> String,
    resolveContext: (ContextReference) -> String = {
        throw SecurityException("Opaque broker context is unavailable")
    },
    observeSensitiveValue: (String) -> Unit = {},
): String = renderForHost(
    maximumOutputLength = maximumOutputLength,
    budget = budget,
    depth = 0,
    resolveSecret = resolveSecret,
    resolveContext = resolveContext,
    observeSensitiveValue = observeSensitiveValue,
).value

private fun BrokerValue.renderForHost(
    maximumOutputLength: Int,
    budget: BrokerValueResolutionBudget,
    depth: Int,
    resolveSecret: (SecretReference) -> String,
    resolveContext: (ContextReference) -> String,
    observeSensitiveValue: (String) -> Unit,
): ResolvedBrokerValue {
    budget.consume(depth)
    val resolved = when (this) {
        is BrokerValue.Literal -> ResolvedBrokerValue(value = value, containsSecret = false)
        is BrokerValue.Secret -> ResolvedBrokerValue(
            value = resolveSecret(reference),
            containsSecret = true,
        )
        is BrokerValue.Context -> ResolvedBrokerValue(
            value = resolveContext(reference),
            containsSecret = true,
        )
        is BrokerValue.Concatenated -> {
            var containsSecret = false
            val value = buildString {
                parts.forEach { part ->
                    val rendered = part.renderForHost(
                        maximumOutputLength = maximumOutputLength,
                        budget = budget,
                        depth = depth + 1,
                        resolveSecret = resolveSecret,
                        resolveContext = resolveContext,
                        observeSensitiveValue = observeSensitiveValue,
                    )
                    containsSecret = containsSecret || rendered.containsSecret
                    append(rendered.value)
                    require(length <= maximumOutputLength) {
                        "Rendered broker value exceeds limit"
                    }
                }
            }
            ResolvedBrokerValue(value = value, containsSecret = containsSecret)
        }
        is BrokerValue.Encoded -> {
            val rendered = value.renderForHost(
                maximumOutputLength = maximumOutputLength,
                budget = budget,
                depth = depth + 1,
                resolveSecret = resolveSecret,
                resolveContext = resolveContext,
                observeSensitiveValue = observeSensitiveValue,
            )
            ResolvedBrokerValue(
                value = when (encoding) {
                    BrokerValueEncoding.JsonString -> Json.encodeToString(
                        JsonPrimitive.serializer(),
                        JsonPrimitive(rendered.value),
                    )
                    BrokerValueEncoding.FormUrlComponent -> URLEncoder.encode(
                        rendered.value,
                        StandardCharsets.UTF_8.name(),
                    )
                    BrokerValueEncoding.Base64 -> Base64.encodeToString(
                        rendered.value.toByteArray(StandardCharsets.UTF_8),
                        Base64.NO_WRAP,
                    )
                },
                containsSecret = rendered.containsSecret,
            )
        }
    }
    require(resolved.value.length <= maximumOutputLength) {
        "Rendered broker value exceeds limit"
    }
    if (resolved.containsSecret && resolved.value.isNotEmpty()) {
        observeSensitiveValue(resolved.value)
    }
    return resolved
}

private data class ResolvedBrokerValue(
    val value: String,
    val containsSecret: Boolean,
)
