package com.m3u.smartphone.ui.material.ktx

import com.m3u.core.foundation.util.basic.sanitizeDisplayText

private const val MAX_SOURCE_REFERENCE_LENGTH = 256

/**
 * Returns only the part of a source reference that is useful for identification
 * without exposing credentials commonly embedded in URLs.
 */
internal fun String.safeSourceReference(): String? {
    // Strip unsafe controls without truncating first. Truncating before removing URL user info
    // could turn a long credential into what looks like a safe authority.
    val clean = sanitizeDisplayText(
        maximumCharacters = length,
        maximumUtf8Bytes = Int.MAX_VALUE,
    ).trim()
    if (clean.isEmpty()) return null

    val schemeSeparator = clean.indexOf("://")
    if (schemeSeparator <= 0) return null
    val scheme = clean.substring(0, schemeSeparator).lowercase()
    val remainder = clean.substring(schemeSeparator + 3)

    return when (scheme) {
        "http", "https" -> {
            val authority = remainder
                .substringBefore('/')
                .substringBefore('?')
                .substringBefore('#')
                .substringAfterLast('@')
                .takeIf(String::isNotBlank)
                ?: return null
            "$scheme://$authority"
                .take(MAX_SOURCE_REFERENCE_LENGTH)
        }

        "content", "file" -> remainder
            .substringBefore('?')
            .substringBefore('#')
            .trimEnd('/')
            .substringAfterLast('/')
            .substringAfterLast(':')
            .takeIf(String::isNotBlank)
            ?.take(MAX_SOURCE_REFERENCE_LENGTH)

        else -> null
    }
}
