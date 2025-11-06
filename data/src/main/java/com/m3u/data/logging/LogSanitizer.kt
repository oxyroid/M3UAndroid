package com.m3u.data.logging

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enhancement #10: Diagnostic Log Sanitization
 * Sanitizes logs by redacting sensitive information before export
 */
@Singleton
class LogSanitizer @Inject constructor() {

    private val timber = Timber.tag("LogSanitizer")

    private var urlsRedacted = 0
    private var tokensRedacted = 0
    private var ipsRedacted = 0
    private var emailsRedacted = 0
    private var pathsRedacted = 0
    private var serialsRedacted = 0
    private var macsRedacted = 0

    companion object {
        private const val REDACTED_URL_PARAMS = "[REDACTED_PARAMS]"
        private const val REDACTED_TOKEN = "[REDACTED_TOKEN]"
        private const val REDACTED_IP = "[REDACTED_IP]"
        private const val REDACTED_EMAIL = "[REDACTED_EMAIL]"
        private const val REDACTED_PATH = "[REDACTED_PATH]"
        private const val REDACTED_SERIAL = "[REDACTED_SERIAL]"
        private const val REDACTED_MAC = "[REDACTED_MAC]"
    }

    /**
     * Main sanitization method - redacts sensitive information from log content
     * @param logContent Raw log content with potential sensitive data
     * @return Sanitized log content safe for export
     */
    fun sanitize(logContent: String): String {
        timber.d("Starting log sanitization...")
        resetCounters()

        var sanitized = logContent

        // Order matters - more specific patterns first
        sanitized = sanitizeBearerTokens(sanitized)
        sanitized = sanitizeAuthTokens(sanitized)
        sanitized = sanitizeUrls(sanitized)
        sanitized = sanitizeEmails(sanitized)
        sanitized = sanitizeMacAddresses(sanitized)
        sanitized = sanitizeIpAddresses(sanitized)
        sanitized = sanitizeDeviceSerials(sanitized)
        sanitized = sanitizeFilePaths(sanitized)

        timber.d("Log sanitization complete: ${getSanitizationSummary()}")
        return sanitized
    }

    /**
     * Sanitize URLs by redacting query parameters
     * @param url URL string that may contain sensitive parameters
     * @return URL with parameters redacted
     */
    fun sanitizeUrl(url: String): String {
        return if (url.contains("?")) {
            val baseUrl = url.substringBefore("?")
            "$baseUrl?$REDACTED_URL_PARAMS"
        } else {
            url
        }
    }

    /**
     * Sanitize authentication tokens
     * @param token Token string to redact
     * @return Redacted token
     */
    fun sanitizeToken(token: String): String {
        return if (token.length > 8) {
            "${token.take(4)}...${REDACTED_TOKEN}"
        } else {
            REDACTED_TOKEN
        }
    }

    /**
     * Get summary of what was sanitized
     * @return Human-readable summary string
     */
    fun getSanitizationSummary(): String {
        return buildString {
            append("Sanitized: ")
            val items = mutableListOf<String>()
            if (urlsRedacted > 0) items.add("$urlsRedacted URLs")
            if (tokensRedacted > 0) items.add("$tokensRedacted tokens")
            if (ipsRedacted > 0) items.add("$ipsRedacted IPs")
            if (emailsRedacted > 0) items.add("$emailsRedacted emails")
            if (pathsRedacted > 0) items.add("$pathsRedacted paths")
            if (serialsRedacted > 0) items.add("$serialsRedacted serials")
            if (macsRedacted > 0) items.add("$macsRedacted MACs")

            if (items.isEmpty()) {
                append("nothing (no sensitive data found)")
            } else {
                append(items.joinToString(", "))
            }
        }
    }

    private fun resetCounters() {
        urlsRedacted = 0
        tokensRedacted = 0
        ipsRedacted = 0
        emailsRedacted = 0
        pathsRedacted = 0
        serialsRedacted = 0
        macsRedacted = 0
    }

    private fun sanitizeUrls(content: String): String {
        return SanitizationPatterns.URL_WITH_PARAMS.replace(content) { matchResult ->
            urlsRedacted++
            sanitizeUrl(matchResult.value)
        }
    }

    private fun sanitizeAuthTokens(content: String): String {
        return SanitizationPatterns.AUTH_TOKEN.replace(content) { matchResult ->
            tokensRedacted++
            "${matchResult.groupValues[1]}=$REDACTED_TOKEN"
        }
    }

    private fun sanitizeBearerTokens(content: String): String {
        return SanitizationPatterns.BEARER_TOKEN.replace(content) {
            tokensRedacted++
            "Bearer $REDACTED_TOKEN"
        }
    }

    private fun sanitizeIpAddresses(content: String): String {
        return SanitizationPatterns.IP_ADDRESS.replace(content) {
            // Preserve localhost and common private IPs for debugging
            val ip = it.value
            if (ip.startsWith("127.") || ip.startsWith("192.168.") ||
                ip.startsWith("10.") || ip.startsWith("172.16.")) {
                ip // Keep private IPs
            } else {
                ipsRedacted++
                REDACTED_IP
            }
        }
    }

    private fun sanitizeEmails(content: String): String {
        return SanitizationPatterns.EMAIL_ADDRESS.replace(content) {
            emailsRedacted++
            REDACTED_EMAIL
        }
    }

    private fun sanitizeFilePaths(content: String): String {
        return SanitizationPatterns.FILE_PATH.replace(content) { matchResult ->
            val path = matchResult.value
            // Keep app-specific paths but redact user paths
            if (path.contains("com.m3u") || path.contains("/system/") ||
                path.contains("/storage/")) {
                // Keep for debugging - these don't expose user data
                path
            } else {
                pathsRedacted++
                REDACTED_PATH
            }
        }
    }

    private fun sanitizeDeviceSerials(content: String): String {
        return SanitizationPatterns.DEVICE_SERIAL.replace(content) { matchResult ->
            serialsRedacted++
            "${matchResult.groupValues[1]}: $REDACTED_SERIAL"
        }
    }

    private fun sanitizeMacAddresses(content: String): String {
        return SanitizationPatterns.MAC_ADDRESS.replace(content) {
            macsRedacted++
            REDACTED_MAC
        }
    }
}
