package com.m3u.extension.api

import kotlinx.serialization.Serializable

/**
 * A fixed HTTP origin declared by an extension.
 *
 * Origins contain only a scheme, host, and optional port. Paths, user information, wildcards,
 * queries, and fragments are deliberately unsupported.
 */
@Serializable
@JvmInline
value class ExtensionNetworkOrigin(val value: String) {
    init {
        require(value.canonicalExtensionNetworkOriginOrNull() != null) {
            "Extension network origin must be an exact HTTP or HTTPS origin"
        }
    }

    val canonicalValue: String
        get() = checkNotNull(value.canonicalExtensionNetworkOriginOrNull())
}

private fun String.canonicalExtensionNetworkOriginOrNull(): String? {
    if (isEmpty() || length > MAX_NETWORK_ORIGIN_LENGTH || any(Char::isWhitespace)) return null
    val schemeSeparator = indexOf(SCHEME_SEPARATOR)
    if (schemeSeparator <= 0) return null
    val scheme = substring(0, schemeSeparator).lowercase()
    if (scheme != HTTP_SCHEME && scheme != HTTPS_SCHEME) return null
    val authority = substring(schemeSeparator + SCHEME_SEPARATOR.length)
    if (
        authority.isEmpty() ||
        authority.any { character ->
            character == '/' ||
                character == '?' ||
                character == '#' ||
                character == '@'
        }
    ) {
        return null
    }

    // The common API has no platform-neutral IP parser. Reject IPv6 literals instead of
    // accepting a loose character pattern that the Android HTTP stack may interpret differently.
    if (authority.startsWith('[') || authority.count { character -> character == ':' } > 1) {
        return null
    }
    val separator = authority.lastIndexOf(':')
    val host = if (separator >= 0) authority.substring(0, separator) else authority
    if (!host.isValidNetworkHost()) return null
    val parsed = ParsedNetworkOrigin(
        host = host.lowercase(),
        port = if (separator >= 0) {
            authority.substring(separator).parseOptionalPort() ?: return null
        } else {
            null
        },
    )
    val port = parsed.port ?: if (scheme == HTTPS_SCHEME) HTTPS_DEFAULT_PORT else HTTP_DEFAULT_PORT
    return "$scheme$SCHEME_SEPARATOR${parsed.host}:$port"
}

private fun String.parseOptionalPort(): Int? {
    if (isEmpty()) return null
    if (first() != ':' || length == 1) return null
    if (drop(1).any { character -> character !in '0'..'9' }) return null
    return drop(1).toIntOrNull()?.takeIf { port -> port in MIN_PORT..MAX_PORT }
}

private fun String.isValidNetworkHost(): Boolean {
    if (isEmpty() || length > MAX_HOST_LENGTH || endsWith('.')) return false
    return split('.').all { label ->
        label.isNotEmpty() &&
            label.length <= MAX_HOST_LABEL_LENGTH &&
            label.first().isAsciiLetterOrDigit() &&
            label.last().isAsciiLetterOrDigit() &&
            label.all { character -> character.isAsciiLetterOrDigit() || character == '-' }
    }
}

private fun Char.isAsciiLetterOrDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'z' || this in 'A'..'Z'

private data class ParsedNetworkOrigin(
    val host: String,
    val port: Int?,
)

private const val SCHEME_SEPARATOR = "://"
private const val HTTP_SCHEME = "http"
private const val HTTPS_SCHEME = "https"
private const val HTTP_DEFAULT_PORT = 80
private const val HTTPS_DEFAULT_PORT = 443
private const val MIN_PORT = 1
private const val MAX_PORT = 65_535
private const val MAX_NETWORK_ORIGIN_LENGTH = 512
private const val MAX_HOST_LENGTH = 253
private const val MAX_HOST_LABEL_LENGTH = 63
