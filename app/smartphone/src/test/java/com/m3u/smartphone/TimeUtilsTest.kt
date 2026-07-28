package com.m3u.smartphone

import com.m3u.smartphone.TimeUtils.formatEOrSh
import java.util.Locale
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class TimeUtilsTest {
    @Test
    fun `twelve hour clock includes localized day period`() {
        assertEquals(
            expected = "1:05:09 PM",
            actual = LocalDateTime(2026, 7, 28, 13, 5, 9).formatEOrSh(
                twelveHourClock = true,
                ignoreSeconds = false,
                locale = Locale.US,
            ),
        )
    }

    @Test
    fun `day period follows locale ordering`() {
        assertEquals(
            expected = "下午1:05",
            actual = formatTimeWithPattern(
                hour = 13,
                minute = 5,
                second = null,
                locale = Locale.SIMPLIFIED_CHINESE,
                pattern = "ah:mm",
            ),
        )
    }

    @Test
    fun `midnight uses twelve rather than zero`() {
        assertEquals(
            expected = "12:05 AM",
            actual = LocalDateTime(2026, 7, 28, 0, 5).formatEOrSh(
                twelveHourClock = true,
                locale = Locale.US,
            ),
        )
    }

    @Test
    fun `twenty four hour timeline keeps end of day hour`() {
        assertEquals(
            expected = "24:00",
            actual = 24f.formatEOrSh(
                use12HourFormat = false,
                locale = Locale.US,
            ),
        )
    }
}
