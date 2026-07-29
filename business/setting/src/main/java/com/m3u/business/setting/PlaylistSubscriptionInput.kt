package com.m3u.business.setting

import com.m3u.core.foundation.util.basic.PlaylistInputKind
import com.m3u.core.foundation.util.basic.normalizePlaylistInputForSubmission
import com.m3u.data.database.model.DataSource
import com.m3u.data.parser.xtream.XtreamInput

internal class SubscriptionDraftSession {
    private var activeKey: String? = null

    fun begin(draftKey: String): Boolean {
        require(draftKey.isNotBlank()) { "Subscription draft key must not be blank" }
        if (activeKey == draftKey) return false
        activeKey = draftKey
        return true
    }
}

internal class ClipboardPlaylistInput private constructor(
    val title: String,
    val m3uUrl: String?,
    val xtreamInput: XtreamInput?,
) {
    companion object {
        fun parse(
            rawUrl: String,
            source: DataSource,
        ): ClipboardPlaylistInput {
            val normalizedUrl = rawUrl.normalizePlaylistInputForSubmission(
                PlaylistInputKind.URL
            )
            val safePathTitle = normalizedUrl
                .safePlaylistFilenameTitleOrNull()
                .orEmpty()

            if (source != DataSource.Xtream) {
                return ClipboardPlaylistInput(
                    title = safePathTitle,
                    m3uUrl = normalizedUrl.takeIf { source == DataSource.M3U },
                    xtreamInput = null,
                )
            }

            val input = XtreamInput.decodeFromPlaylistUrlOrNull(normalizedUrl)
            return ClipboardPlaylistInput(
                // An account URL does not contain a trustworthy user-facing playlist name.
                // Keep the surrounding localized title field explicit instead of inventing an
                // English timestamp label.
                title = "",
                m3uUrl = null,
                xtreamInput = input?.copy(
                    basicUrl = input.basicUrl.normalizePlaylistInputForSubmission(
                        PlaylistInputKind.BASE_URL
                    ),
                    username = input.username.normalizePlaylistInputForSubmission(
                        PlaylistInputKind.USERNAME
                    ),
                    password = input.password.normalizePlaylistInputForSubmission(
                        PlaylistInputKind.PASSWORD
                    ),
                ),
            )
        }
    }
}

private fun String.safePlaylistFilenameTitleOrNull(): String? {
    val withoutFragment = substringBefore('#')
    val withoutQuery = withoutFragment.substringBefore('?')
    val schemeSeparator = withoutQuery.indexOf("://")
    val encodedFilename = if (schemeSeparator >= 0) {
        val authorityStart = schemeSeparator + 3
        val pathStart = withoutQuery.indexOf('/', authorityStart)
        if (pathStart < 0) return null
        withoutQuery.substring(pathStart + 1).substringAfterLast('/')
    } else {
        val filename = withoutQuery.substringAfterLast('/')
        if (
            '/' !in withoutQuery &&
            filename.any { character ->
                character == '@' || character == ':' || character == '=' || character == '&'
            }
        ) {
            return null
        }
        filename
    }
    if (encodedFilename.isBlank()) return null

    val decodedFilename = encodedFilename
        .decodePercentEncodedPathSegment()
        .substringAfterLast('/')
        .substringAfterLast('\\')
    val extensionSeparator = decodedFilename.lastIndexOf('.')
    val filenameWithoutExtension = if (extensionSeparator > 0) {
        decodedFilename.substring(0, extensionSeparator)
    } else {
        decodedFilename
    }
    val normalized = filenameWithoutExtension.normalizePlaylistInputForSubmission(
        PlaylistInputKind.TITLE
    )
    if (
        normalized.isBlank() ||
        normalized == "." ||
        normalized == ".." ||
        normalized.containsSensitiveTitleMaterial()
    ) {
        return null
    }
    return normalized
}

private fun String.containsSensitiveTitleMaterial(): Boolean {
    if (any { character ->
            character == '@' ||
                character == '=' ||
                character == '&' ||
                character == '?' ||
                character == '#' ||
                character == ':'
        }
    ) {
        return true
    }
    return SENSITIVE_TITLE_WORDS.any { word -> contains(word, ignoreCase = true) }
}

private fun String.decodePercentEncodedPathSegment(): String {
    if ('%' !in this) return this

    val output = StringBuilder(length)
    var index = 0
    while (index < length) {
        if (this[index] != '%' || index + 2 >= length) {
            output.append(this[index])
            index++
            continue
        }

        val bytes = mutableListOf<Byte>()
        var encodedIndex = index
        while (
            encodedIndex + 2 < length &&
            this[encodedIndex] == '%'
        ) {
            val high = this[encodedIndex + 1].hexDigitOrNull() ?: break
            val low = this[encodedIndex + 2].hexDigitOrNull() ?: break
            bytes += ((high shl 4) or low).toByte()
            encodedIndex += 3
        }
        if (bytes.isEmpty()) {
            output.append(this[index])
            index++
        } else {
            output.append(bytes.toByteArray().decodeToString())
            index = encodedIndex
        }
    }
    return output.toString()
}

private fun Char.hexDigitOrNull(): Int? = when (this) {
    in '0'..'9' -> code - '0'.code
    in 'a'..'f' -> code - 'a'.code + 10
    in 'A'..'F' -> code - 'A'.code + 10
    else -> null
}

private val SENSITIVE_TITLE_WORDS = setOf(
    "authorization",
    "credential",
    "passwd",
    "password",
    "secret",
    "token",
    "username",
)
