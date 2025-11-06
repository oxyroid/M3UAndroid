package com.m3u.data.logging

/**
 * Enhancement #10: Diagnostic Log Sanitization
 * Regex patterns for identifying and redacting sensitive information
 * Patterns are compiled once and reused for performance
 */
object SanitizationPatterns {
    /** Matches URLs with query parameters */
    val URL_WITH_PARAMS by lazy { Regex("(https?://[^\\s]+\\?[^\\s]*)") }

    /** Matches authentication tokens, keys, passwords in query strings */
    val AUTH_TOKEN by lazy { Regex("(token|key|password|auth|secret|api[-_]?key)=([^&\\s]+)", RegexOption.IGNORE_CASE) }

    /** Matches IPv4 addresses */
    val IP_ADDRESS by lazy { Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b") }

    /** Matches email addresses */
    val EMAIL_ADDRESS by lazy { Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}") }

    /** Matches file paths (Unix and Windows style) */
    val FILE_PATH by lazy { Regex("(?:/[^\\s]+|[A-Z]:\\\\[^\\s]+)") }

    /** Matches device serial numbers and IDs */
    val DEVICE_SERIAL by lazy { Regex("(serial|device[-_]?id)[:\\s=]+([A-Z0-9-]+)", RegexOption.IGNORE_CASE) }

    /** Matches MAC addresses */
    val MAC_ADDRESS by lazy { Regex("([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})") }

    /** Matches bearer tokens in headers */
    val BEARER_TOKEN by lazy { Regex("Bearer\\s+[A-Za-z0-9._~+/-]+=*", RegexOption.IGNORE_CASE) }
}
