package com.m3u.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.text.BidiFormatter
import androidx.core.text.TextDirectionHeuristicsCompat
import java.text.BreakIterator
import java.util.Locale

private const val TV_READABLE_SEGMENT_GRAPHEME_LIMIT = 128

@Composable
internal fun rememberTvBidiFormatter(): TvBidiFormatter {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return remember(isRtl) { TvBidiFormatter(isRtl) }
}

internal class TvBidiFormatter(isRtlContext: Boolean) {
    private val formatter = BidiFormatter.getInstance(isRtlContext)

    fun natural(value: String): String = formatter.unicodeWrap(value.withoutBidiControls())

    fun ltr(value: String): String = formatter.unicodeWrap(
        value.withoutBidiControls(),
        TextDirectionHeuristicsCompat.LTR,
    )
}

internal fun String.withoutBidiControls(): String = filterNot { character ->
    character == '\u061C' ||
        character == '\u200E' ||
        character == '\u200F' ||
        character.code in 0x202A..0x202E ||
        character.code in 0x2066..0x2069
}

/**
 * Splits extension-owned text before applying direction isolation.
 *
 * BidiFormatter adds paired control characters. Splitting an already wrapped
 * value can leave one Text node with an opening control and another with its
 * closing control, so the transform must always run on each completed segment.
 */
internal fun String.tvReadableSegments(
    transform: (String) -> String = { it },
): List<String> {
    if (isEmpty()) return listOf(transform(""))

    val iterator = BreakIterator.getCharacterInstance(Locale.getDefault()).apply {
        setText(this@tvReadableSegments)
    }
    val segments = mutableListOf<String>()
    var segmentStart = iterator.first()
    var boundary = iterator.next()
    var graphemeCount = 0

    while (boundary != BreakIterator.DONE) {
        graphemeCount++
        if (graphemeCount >= TV_READABLE_SEGMENT_GRAPHEME_LIMIT) {
            segments += transform(substring(segmentStart, boundary))
            segmentStart = boundary
            graphemeCount = 0
        }
        boundary = iterator.next()
    }
    if (segmentStart < length) {
        segments += transform(substring(segmentStart))
    }
    return segments.ifEmpty { listOf(transform(this)) }
}
