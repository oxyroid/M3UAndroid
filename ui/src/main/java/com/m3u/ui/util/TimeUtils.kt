package com.m3u.ui.util

import kotlinx.datetime.LocalDateTime

object TimeUtils {
    fun LocalDateTime.toEOrSh(): Float = run { hour + minute / 60f + second / 3600f }

    fun LocalDateTime.formatEOrSh(): String =
        "${if (hour < 10) "0$hour" else hour}:" +
                "${if (minute < 10) "0$minute" else minute}:" +
                "${if (second < 10) "0$second" else second}"

    fun Float.formatEOrSh(): String = buildString {
        append((this@formatEOrSh / 1).toInt().let { if (it < 10) "0$it" else "$it" })
        append(":")
        append((this@formatEOrSh % 1 * 60).toInt().let { if (it < 10) "0$it" else "$it" })
    }
}