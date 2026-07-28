package com.m3u.smartphone.ui.material.components.mask

import kotlin.test.Test
import kotlin.test.assertEquals

class MaskStateTest {
    @Test
    fun `recommended duration never shortens the default`() {
        assertEquals(
            expected = 4L,
            actual = calculateRecommendedMaskDurationSeconds(
                baseDurationSeconds = 4L,
                recommendedDurationMillis = 2_000L,
            ),
        )
    }

    @Test
    fun `recommended duration rounds partial seconds up`() {
        assertEquals(
            expected = 7L,
            actual = calculateRecommendedMaskDurationSeconds(
                baseDurationSeconds = 4L,
                recommendedDurationMillis = 6_001L,
            ),
        )
    }

    @Test
    fun `infinite recommendation disables automatic timeout`() {
        assertEquals(
            expected = Long.MAX_VALUE,
            actual = calculateRecommendedMaskDurationSeconds(
                baseDurationSeconds = 4L,
                recommendedDurationMillis = Long.MAX_VALUE,
            ),
        )
    }
}
