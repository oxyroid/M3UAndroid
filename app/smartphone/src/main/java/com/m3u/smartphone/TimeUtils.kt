package com.m3u.smartphone

import kotlinx.datetime.LocalDateTime

object TimeUtils {
    fun LocalDateTime.toEOrSh(): Float = run { hour + minute / 60f + second / 3600f }

    fun LocalDateTime.formatEOrSh(
        twelveHourClock: Boolean,
        ignoreSeconds: Boolean = true
    ): String {
        return if (twelveHourClock) {
            val hour12 = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour
            if (ignoreSeconds) {
                "%02d:%02d".format(hour12, minute)
            } else {
                "%02d:%02d:%02d".format(hour12, minute, second)
            }
        } else {
            if (ignoreSeconds) {
                "%02d:%02d".format(hour, minute)
            } else {
                "%02d:%02d:%02d".format(hour, minute, second)
            }
        }
    }

    fun Float.formatEOrSh(use12HourFormat: Boolean): String {
        val hour = (this / 1).toInt()
        val minute = (this % 1 * 60).toInt()
        val hour12 = when {
            !use12HourFormat -> hour
            hour > 12 -> hour - 12
            hour == 0 -> 12
            else -> hour
        }
        return if (use12HourFormat) {
            val amPm = if (hour < 12) "AM" else "PM"
            "%02d:%02d %s".format(hour12, minute, amPm)
        } else {
            "%02d:%02d".format(hour12, minute)
        }
    }
}