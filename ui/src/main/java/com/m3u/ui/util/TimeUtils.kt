package com.m3u.ui.util

import kotlinx.datetime.LocalDateTime

object TimeUtils {
    fun LocalDateTime.toEOrSh(): Float = run { hour + minute / 60f + second / 3600f }

    fun LocalDateTime.formatEOrSh(
        twelveHourClock: Boolean,
        ignoreSeconds: Boolean = true
    ): String {
        return if (twelveHourClock) {
            val hour12 = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour
            val formattedHour = if (hour12 < 10) "0$hour12" else hour12.toString()
            val formattedMinute = if (minute < 10) "0$minute" else minute.toString()
            val formattedSecond = if (second < 10) "0$second" else second.toString()
            buildString {
                append("$formattedHour:")
                append(formattedMinute)
                if (!ignoreSeconds) {
                    append(":$formattedSecond")
                }
            }
        } else {
            buildString {
                append("${if (hour < 10) "0$hour" else hour}:")
                append("${if (minute < 10) "0$minute" else minute}")
                if (!ignoreSeconds) {
                    append(":${if (second < 10) "0$second" else second}")
                }
            }
        }
    }

    fun Float.formatEOrSh(use12HourFormat: Boolean): String {
        val hour = (this / 1).toInt()
        val minute = (this % 1 * 60).toInt()
        val amPm = if (hour < 12) "AM" else "PM"
        val hour12 = when {
            !use12HourFormat -> hour
            hour > 12 -> hour - 12
            hour == 0 -> 12
            else -> hour
        }
        val formattedHour = if (hour12 < 10) "0$hour12" else hour12.toString()
        val formattedMinute = if (minute < 10) "0$minute" else minute.toString()
        return if (use12HourFormat) {
            "$formattedHour:$formattedMinute $amPm"
        } else {
            "$formattedHour:$formattedMinute"// $amPm"
        }

    }
}