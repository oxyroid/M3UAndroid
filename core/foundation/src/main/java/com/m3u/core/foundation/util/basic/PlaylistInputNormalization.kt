package com.m3u.core.foundation.util.basic

/**
 * User-controlled playlist fields have different practical size limits and whitespace
 * semantics. The limits are deliberately below Android WorkManager's aggregate input limit
 * because several Xtream fields can be submitted together.
 */
enum class PlaylistInputKind(
    val maximumCharacters: Int,
    val maximumUtf8Bytes: Int,
    internal val trimOnSubmission: Boolean,
) {
    TITLE(
        maximumCharacters = 256,
        maximumUtf8Bytes = 1_024,
        trimOnSubmission = true,
    ),
    URL(
        maximumCharacters = 4_096,
        maximumUtf8Bytes = 4_096,
        trimOnSubmission = true,
    ),
    BASE_URL(
        maximumCharacters = 2_048,
        maximumUtf8Bytes = 2_048,
        trimOnSubmission = true,
    ),
    USERNAME(
        maximumCharacters = 256,
        maximumUtf8Bytes = 1_024,
        trimOnSubmission = true,
    ),
    PASSWORD(
        maximumCharacters = 1_024,
        maximumUtf8Bytes = 1_024,
        trimOnSubmission = false,
    ),
    USER_AGENT(
        maximumCharacters = 1_024,
        maximumUtf8Bytes = 2_048,
        trimOnSubmission = true,
    ),
    EPG_URL(
        maximumCharacters = 4_096,
        maximumUtf8Bytes = 4_096,
        trimOnSubmission = true,
    ),
}

/**
 * Removes invisible direction overrides, line breaks, and other control characters before a
 * value enters long-lived feature state. Leading and trailing spaces are intentionally retained
 * here so a password containing them is not silently changed.
 */
fun String.sanitizePlaylistInput(kind: PlaylistInputKind): String {
    return sanitizeDisplayText(
        maximumCharacters = kind.maximumCharacters,
        maximumUtf8Bytes = kind.maximumUtf8Bytes,
    )
}

/**
 * Removes control characters that can change layout, spoof direction, or split a value across
 * lines, then applies explicit code-point and UTF-8 limits. Callers that need to inspect a complete
 * value before reducing it (for example, to remove URL user info) can pass the source length and
 * [Int.MAX_VALUE], then bound only the safe result they display.
 */
fun String.sanitizeDisplayText(
    maximumCharacters: Int,
    maximumUtf8Bytes: Int,
): String {
    if (isEmpty()) return this
    require(maximumCharacters >= 0) { "maximumCharacters must not be negative" }
    require(maximumUtf8Bytes >= 0) { "maximumUtf8Bytes must not be negative" }

    val output = StringBuilder(minOf(length, maximumCharacters))
    var inputIndex = 0
    var outputCharacters = 0
    var outputUtf8Bytes = 0
    while (
        inputIndex < length &&
        outputCharacters < maximumCharacters
    ) {
        val first = this[inputIndex]
        val second = getOrNull(inputIndex + 1)
        val codePoint: Int
        val inputWidth: Int
        when {
            first.isHighSurrogate() && second?.isLowSurrogate() == true -> {
                codePoint = 0x10000 +
                    ((first.code - HIGH_SURROGATE_START) shl 10) +
                    (second.code - LOW_SURROGATE_START)
                inputWidth = 2
            }

            first.isSurrogate() -> {
                inputIndex++
                continue
            }

            else -> {
                codePoint = first.code
                inputWidth = 1
            }
        }
        inputIndex += inputWidth

        if (codePoint.isUnsafePlaylistInputCodePoint()) continue

        val encodedBytes = codePoint.utf8Length()
        if (outputUtf8Bytes + encodedBytes > maximumUtf8Bytes) break

        output.append(first)
        if (inputWidth == 2) output.append(second)
        outputCharacters++
        outputUtf8Bytes += encodedBytes
    }
    return output.toString()
}

/**
 * Applies field-specific whitespace semantics immediately before persistence, WorkManager, IPC,
 * or network submission. Passwords are sanitized and bounded but never trimmed.
 */
fun String.normalizePlaylistInputForSubmission(kind: PlaylistInputKind): String {
    val sanitized = sanitizePlaylistInput(kind)
    return if (kind.trimOnSubmission) sanitized.trim() else sanitized
}

private fun Int.isUnsafePlaylistInputCodePoint(): Boolean =
    this in BIDI_CONTROL_CODE_POINTS ||
        this == LINE_SEPARATOR ||
        this == PARAGRAPH_SEPARATOR ||
        this <= Char.MAX_VALUE.code && toChar().isISOControl()

private fun Int.utf8Length(): Int = when {
    this <= 0x7F -> 1
    this <= 0x7FF -> 2
    this <= 0xFFFF -> 3
    else -> 4
}

private fun Char.isSurrogate(): Boolean = isHighSurrogate() || isLowSurrogate()

private val BIDI_CONTROL_CODE_POINTS = (
    (0x202A..0x202E) +
        (0x2066..0x2069) +
        listOf(0x061C, 0x200E, 0x200F)
    ).toSet()

private const val HIGH_SURROGATE_START = 0xD800
private const val LOW_SURROGATE_START = 0xDC00
private const val LINE_SEPARATOR = 0x2028
private const val PARAGRAPH_SEPARATOR = 0x2029
