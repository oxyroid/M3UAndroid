package com.m3u.features.stream

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

internal object Utils {
    fun Instant.toEOrSh(): Float =
        toLocalDateTime(TimeZone.currentSystemDefault())
            .run { hour + minute / 60f + second / 3600f }

    fun Float.formatEOrSh(): String = buildString {
        append((this@formatEOrSh / 1).toInt().let { if (it < 10) "0$it" else "$it" })
        append(":")
        append((this@formatEOrSh % 1 * 60).toInt().let { if (it < 10) "0$it" else "$it" })
    }
}