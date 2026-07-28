package com.m3u.smartphone.ui.business.setting.fragments

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.text.BidiFormatter
import androidx.core.text.TextDirectionHeuristicsCompat

@Composable
internal fun rememberUiBidiFormatter(): UiBidiFormatter {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return remember(isRtl) { UiBidiFormatter(isRtl) }
}

internal class UiBidiFormatter(isRtlContext: Boolean) {
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
