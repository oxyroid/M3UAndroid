package com.m3u.data.repository.playlist

import android.content.ContentResolver
import android.net.Uri
import com.m3u.core.util.basic.startWithHttpScheme
import com.m3u.core.util.basic.startsWithAny
import java.net.URI

internal object PlaylistNetworkUrl {
    fun normalizeM3uInput(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty() || isSupportedAndroidUrl(trimmed)) return trimmed
        if (trimmed.startsWith("/")) return URI("file", "", trimmed, null).toASCIIString()
        return if (trimmed.startWithHttpScheme()) trimmed else "http://$trimmed"
    }

    fun isSupportedNetworkUrl(url: String): Boolean = url.startsWithAny(
        "http://",
        "https://",
        ignoreCase = true
    )

    fun isSupportedAndroidUrl(url: String): Boolean = url.startsWithAny(
        "${ContentResolver.SCHEME_FILE}:",
        "${ContentResolver.SCHEME_CONTENT}:",
        ignoreCase = true
    )

    fun normalizeAndroidFileUrl(url: String): String {
        val fileScheme = "${ContentResolver.SCHEME_FILE}:"
        if (!url.startsWith(fileScheme, ignoreCase = true)) return url
        return decodePercentEncoded(url)
    }

    fun resolveInternalFileName(
        uri: Uri,
        displayName: String?,
        fallbackName: String
    ): String = resolveInternalFileName(
        displayName = displayName,
        lastPathSegment = uri.lastPathSegment,
        fallbackName = fallbackName
    )

    internal fun resolveInternalFileName(
        displayName: String?,
        lastPathSegment: String?,
        fallbackName: String
    ): String = displayName?.stableFileName()
        ?: lastPathSegment?.stableFileName()
        ?: fallbackName

    internal fun resolveOwnFilesProviderRelativePath(
        authority: String?,
        pathSegments: List<String>,
        packageName: String
    ): String? {
        if (authority != "$packageName.provider") return null
        if (pathSegments.firstOrNull() != "files") return null
        return pathSegments
            .drop(1)
            .joinToString("/")
            .takeIf { it.isNotBlank() }
    }

    fun httpFallbackForPlainHttpTlsFailure(
        url: String,
        responseMessage: String,
        responseBody: String
    ): String? {
        if (!url.startsWith("https://", ignoreCase = true)) return null
        if (!isPlainHttpTlsFailure(responseMessage) && !isPlainHttpTlsFailure(responseBody)) {
            return null
        }
        return "http://${url.drop("https://".length)}"
    }

    private fun isPlainHttpTlsFailure(text: String): Boolean {
        return text.contains("unable to parse tls packet header", ignoreCase = true) ||
            text.contains("not an ssl/tls record", ignoreCase = true) ||
            text.contains("ssl_error", ignoreCase = true)
    }

    private fun decodePercentEncoded(value: String): String {
        if ('%' !in value) return value

        val decoded = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            if (value[index] != '%' || index + 2 >= value.length) {
                decoded.append(value[index])
                index += 1
                continue
            }

            val bytes = mutableListOf<Byte>()
            var next = index
            while (next + 2 < value.length && value[next] == '%') {
                val high = hexValue(value[next + 1])
                val low = hexValue(value[next + 2])
                if (high == null || low == null) break
                bytes += ((high shl 4) + low).toByte()
                next += 3
            }

            if (bytes.isEmpty()) {
                decoded.append(value[index])
                index += 1
            } else {
                decoded.append(bytes.toByteArray().toString(Charsets.UTF_8))
                index = next
            }
        }
        return decoded.toString()
    }

    private fun String.stableFileName(): String? = trim()
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .substringAfterLast(':')
        .takeIf { it.isNotBlank() }

    private fun hexValue(char: Char): Int? = when (char) {
        in '0'..'9' -> char - '0'
        in 'a'..'f' -> char - 'a' + 10
        in 'A'..'F' -> char - 'A' + 10
        else -> null
    }
}
