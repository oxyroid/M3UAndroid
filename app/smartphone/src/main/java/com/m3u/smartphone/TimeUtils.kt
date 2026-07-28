package com.m3u.smartphone

import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.datetime.LocalDateTime

object TimeUtils {
    fun LocalDateTime.toEOrSh(): Float = run { hour + minute / 60f + second / 3600f }

    fun LocalDateTime.formatEOrSh(
        twelveHourClock: Boolean,
        ignoreSeconds: Boolean = true,
        locale: Locale = Locale.getDefault(Locale.Category.FORMAT),
    ): String {
        return formatTime(
            hour = hour,
            minute = minute,
            second = second.takeUnless { ignoreSeconds },
            twelveHourClock = twelveHourClock,
            locale = locale,
        )
    }

    fun Float.formatEOrSh(
        use12HourFormat: Boolean,
        locale: Locale = Locale.getDefault(Locale.Category.FORMAT),
    ): String {
        val hour = (this / 1).toInt()
        val minute = (this % 1 * 60).toInt()
        return formatTime(hour, minute, null, use12HourFormat, locale)
    }

    private fun formatTime(
        hour: Int,
        minute: Int,
        second: Int?,
        twelveHourClock: Boolean,
        locale: Locale,
    ): String {
        val skeleton = buildString {
            append(if (twelveHourClock) "hm" else "Hm")
            if (second != null) append('s')
        }
        val pattern = runCatching {
            DateFormat.getBestDateTimePattern(locale, skeleton)
        }.getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: fallbackTimePattern(locale, twelveHourClock, second != null)
        val endpointPattern = if (!twelveHourClock && hour == 24) {
            pattern.replaceUnquotedHourSymbol(from = 'H', to = 'k')
        } else {
            pattern
        }
        return formatTimeWithPattern(
            hour = hour,
            minute = minute,
            second = second,
            locale = locale,
            pattern = endpointPattern,
        )
    }
}

internal fun formatTimeWithPattern(
    hour: Int,
    minute: Int,
    second: Int?,
    locale: Locale,
    pattern: String,
): String {
    val calendar = Calendar.getInstance(locale).apply {
        clear()
        set(2000, Calendar.JANUARY, 1, hour % 24, minute, second ?: 0)
    }
    return SimpleDateFormat(pattern, locale).format(calendar.time)
}

private fun fallbackTimePattern(
    locale: Locale,
    twelveHourClock: Boolean,
    includeSeconds: Boolean,
): String {
    val defaultPattern = (
        java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, locale)
            as? SimpleDateFormat
        )?.toPattern().orEmpty()
    val dayPeriodBeforeHour = defaultPattern.unquotedIndexOf('a')
        .takeIf { it >= 0 }
        ?.let { periodIndex ->
            val hourIndex = listOf('h', 'H', 'k', 'K')
                .map(defaultPattern::unquotedIndexOf)
                .filter { it >= 0 }
                .minOrNull()
                ?: Int.MAX_VALUE
            periodIndex < hourIndex
        } == true
    val time = if (includeSeconds) "h:mm:ss" else "h:mm"
    return when {
        !twelveHourClock -> if (includeSeconds) "HH:mm:ss" else "HH:mm"
        dayPeriodBeforeHour -> "a$time"
        else -> "$time a"
    }
}

private fun String.unquotedIndexOf(target: Char): Int {
    var quoted = false
    forEachIndexed { index, character ->
        if (character == '\'') {
            quoted = !quoted
        } else if (!quoted && character == target) {
            return index
        }
    }
    return -1
}

private fun String.replaceUnquotedHourSymbol(from: Char, to: Char): String = buildString(length) {
    var quoted = false
    this@replaceUnquotedHourSymbol.forEach { character ->
        if (character == '\'') {
            quoted = !quoted
            append(character)
        } else {
            append(if (!quoted && character == from) to else character)
        }
    }
}
