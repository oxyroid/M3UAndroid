package com.m3u.smartphone.ui.material.ktx

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.text.BidiFormatter
import androidx.core.text.TextDirectionHeuristicsCompat
import com.m3u.core.foundation.util.basic.sanitizeDisplayText

@Composable
internal fun rememberUiBidiFormatter(): UiBidiFormatter {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return remember(isRtl) { UiBidiFormatter(isRtl) }
}

internal class UiBidiFormatter(isRtlContext: Boolean) {
    private val formatter = BidiFormatter.getInstance(isRtlContext)

    fun natural(
        value: String,
        maximumCharacters: Int = DEFAULT_DISPLAY_CHARACTER_LIMIT,
    ): String = formatter.unicodeWrap(
        value.safeDisplayText(maximumCharacters),
    )

    fun ltr(value: String): String = formatter.unicodeWrap(
        value.safeDisplayText(),
        TextDirectionHeuristicsCompat.LTR,
    )

    /**
     * Sanitizes a standalone technical value without adding bidirectional formatting controls.
     *
     * The returned plain text keeps its original character order and full sanitized length, so it
     * can be displayed with an LTR text direction and copied without hidden formatter markers.
     */
    fun standaloneTechnical(value: String): String = value.withoutBidiControls()
}

internal fun String.safeDisplayText(
    maximumCharacters: Int = DEFAULT_DISPLAY_CHARACTER_LIMIT,
): String = sanitizeDisplayText(
    maximumCharacters = maximumCharacters,
    maximumUtf8Bytes = maximumCharacters.saturatingTimes(MAX_UTF8_BYTES_PER_CODE_POINT),
)

internal fun String.withoutBidiControls(): String = sanitizeDisplayText(
    maximumCharacters = length,
    maximumUtf8Bytes = Int.MAX_VALUE,
)

private fun Int.saturatingTimes(multiplier: Int): Int =
    if (this > Int.MAX_VALUE / multiplier) Int.MAX_VALUE else this * multiplier

private const val DEFAULT_DISPLAY_CHARACTER_LIMIT = 256
private const val MAX_UTF8_BYTES_PER_CODE_POINT = 4
