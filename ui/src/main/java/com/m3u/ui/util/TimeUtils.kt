package com.m3u.ui.util

import kotlinx.datetime.LocalDateTime

object TimeUtils {
    fun LocalDateTime.toEOrSh(): Float = run { hour + minute / 60f + second / 3600f }

    fun LocalDateTime.formatEOrSh(use12HourFormat: Boolean): String {
        return if (use12HourFormat) {
            val hour12 = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour
            val amPm = if (hour < 12) "AM" else "PM"
            val formattedHour = if (hour12 < 10) "0$hour12" else hour12.toString()
            val formattedMinute = if (minute < 10) "0$minute" else minute.toString()
            val formattedSecond = if (second < 10) "0$second" else second.toString()
//        return "$formattedHour:$formattedMinute:$formattedSecond $amPm"
            return "$formattedHour:$formattedMinute"
        } else {
            "${if (hour < 10) "0$hour" else hour}:" +
            "${if (minute < 10) "0$minute" else minute}"// +
//            ":${if (second < 10) "0$second" else second}"
        }
    }
    fun Float.formatEOrSh(use12HourFormat: Boolean): String {
        val hour = (this / 1).toInt()
        val minute = (this % 1 * 60).toInt()
        val amPm = if (hour < 12) "AM" else "PM"
        val hour12 = if (use12HourFormat) {
            if (hour > 12) hour - 12 else if (hour == 0) 12 else hour
        } else {
            hour
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