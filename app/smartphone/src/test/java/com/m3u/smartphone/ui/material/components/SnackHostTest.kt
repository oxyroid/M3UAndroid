package com.m3u.smartphone.ui.material.components

import kotlin.test.Test
import kotlin.test.assertEquals

class SnackHostTest {
    @Test
    fun `accessibility recommendation can extend the message`() {
        assertEquals(
            expected = 12_000L,
            actual = calculateSnackTimeoutMillis(
                originalTimeoutMillis = 3_000L,
                recommendedTimeoutMillis = 12_000L,
            ),
        )
    }

    @Test
    fun `recommendation never shortens the message`() {
        assertEquals(
            expected = 3_000L,
            actual = calculateSnackTimeoutMillis(
                originalTimeoutMillis = 3_000L,
                recommendedTimeoutMillis = 1_000L,
            ),
        )
    }

    @Test
    fun `infinite recommendation remains infinite`() {
        assertEquals(
            expected = Long.MAX_VALUE,
            actual = calculateSnackTimeoutMillis(
                originalTimeoutMillis = 3_000L,
                recommendedTimeoutMillis = Long.MAX_VALUE,
            ),
        )
    }
}
