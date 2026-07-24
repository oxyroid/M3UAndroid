package com.m3u.smartphone.ui.navigation

import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloatingNavigationMotionTest {
    @Test
    fun `invalidating a settle cancels its job and retires its generation`() {
        val controller = FloatingNavigationSettleController()
        val firstJob = Job()
        controller.job = firstJob

        val firstGeneration = controller.invalidate()
        assertFalse(firstJob.isActive)
        assertTrue(controller.isCurrent(firstGeneration))

        val secondJob = Job()
        controller.job = secondJob
        val secondGeneration = controller.invalidate()

        assertFalse(secondJob.isActive)
        assertFalse(controller.isCurrent(firstGeneration))
        assertTrue(controller.isCurrent(secondGeneration))
    }

    @Test
    fun `indicator physical position mirrors destination slots in rtl`() {
        assertEquals(
            4f,
            resolveFloatingNavigationIndicatorPhysicalX(
                position = 0f,
                itemWidthPx = 76f,
                navigationWidthPx = 236f,
                innerPaddingPx = 4f,
                isRtl = false,
            ),
        )
        assertEquals(
            156f,
            resolveFloatingNavigationIndicatorPhysicalX(
                position = 0f,
                itemWidthPx = 76f,
                navigationWidthPx = 236f,
                innerPaddingPx = 4f,
                isRtl = true,
            ),
        )
        assertEquals(
            4f,
            resolveFloatingNavigationIndicatorPhysicalX(
                position = 2f,
                itemWidthPx = 76f,
                navigationWidthPx = 236f,
                innerPaddingPx = 4f,
                isRtl = true,
            ),
        )
    }

    @Test
    fun `drag follows the pointer with resistance and bounded overscroll`() {
        assertEquals(
            1.485f,
            applyFloatingNavigationDrag(
                desiredPosition = 1f,
                deltaItems = 0.5f,
                itemCount = 3,
            ),
            absoluteTolerance = 0.0001f,
        )
        assertEquals(
            2.285f,
            applyFloatingNavigationDrag(
                desiredPosition = 2.17f,
                deltaItems = 0.5f,
                itemCount = 3,
            ),
            absoluteTolerance = 0.0001f,
        )
        assertEquals(
            2.38f,
            applyFloatingNavigationDrag(
                desiredPosition = 2.4f,
                deltaItems = 2f,
                itemCount = 3,
            ),
        )
    }

    @Test
    fun `release projects velocity but adds at most one slot from the nearest anchor`() {
        assertEquals(
            2,
            resolveFloatingNavigationReleaseTarget(
                desiredPosition = 0.6f,
                velocityPxPerSecond = 1000f,
                itemWidthPx = 100f,
                itemCount = 4,
            ),
        )
        assertEquals(
            0,
            resolveFloatingNavigationReleaseTarget(
                desiredPosition = 0.4f,
                velocityPxPerSecond = -1000f,
                itemWidthPx = 100f,
                itemCount = 4,
            ),
        )
    }

    @Test
    fun `idle morph is the identity regardless of residual velocity`() {
        assertEquals(
            FloatingNavigationIndicatorMorph(
                scaleX = 1f,
                scaleY = 1f,
                leadOffsetFraction = 0f,
                refractionProgress = 0f,
                pressureProgress = 0f,
            ),
            resolveFloatingNavigationIndicatorMorph(
                interactionProgress = 0f,
                velocityItemsPerSecond = 100f,
            ),
        )
    }

    @Test
    fun `stationary press expands evenly and enables refraction`() {
        val morph = resolveFloatingNavigationIndicatorMorph(
            interactionProgress = 1f,
            velocityItemsPerSecond = 0f,
        )

        assertTrue(morph.scaleX > 1f)
        assertEquals(morph.scaleX, morph.scaleY, absoluteTolerance = 0.0001f)
        assertEquals(0f, morph.leadOffsetFraction)
        assertTrue(morph.refractionProgress in 0f..1f)
        assertEquals(1f, morph.pressureProgress)
    }

    @Test
    fun `mid gesture stretches with speed and reverses only the optical lead`() {
        val forward = resolveFloatingNavigationIndicatorMorph(
            interactionProgress = 0.5f,
            velocityItemsPerSecond = 3.25f,
        )
        val reverse = resolveFloatingNavigationIndicatorMorph(
            interactionProgress = 0.5f,
            velocityItemsPerSecond = -3.25f,
        )

        assertTrue(forward.scaleX > forward.scaleY)
        assertEquals(forward.scaleX, reverse.scaleX, absoluteTolerance = 0.0001f)
        assertEquals(forward.scaleY, reverse.scaleY, absoluteTolerance = 0.0001f)
        assertEquals(
            forward.refractionProgress,
            reverse.refractionProgress,
            absoluteTolerance = 0.0001f,
        )
        assertEquals(
            forward.leadOffsetFraction,
            -reverse.leadOffsetFraction,
            absoluteTolerance = 0.0001f,
        )
        assertEquals(0.5f, forward.pressureProgress, absoluteTolerance = 0.0001f)
    }

    @Test
    fun `out of range and non finite motion inputs remain bounded and finite`() {
        val overflow = resolveFloatingNavigationIndicatorMorph(
            interactionProgress = Float.POSITIVE_INFINITY,
            velocityItemsPerSecond = Float.NEGATIVE_INFINITY,
        )
        val invalid = resolveFloatingNavigationIndicatorMorph(
            interactionProgress = Float.NaN,
            velocityItemsPerSecond = Float.NaN,
        )

        assertTrue(overflow.scaleX.isFinite())
        assertTrue(overflow.scaleY.isFinite())
        assertTrue(overflow.scaleX in 1f..2f)
        assertTrue(overflow.scaleY in 1f..2f)
        assertTrue(overflow.leadOffsetFraction in -0.1f..0.1f)
        assertTrue(overflow.refractionProgress in 0f..1f)
        assertEquals(1f, overflow.pressureProgress)
        assertEquals(1f, invalid.scaleX)
        assertEquals(1f, invalid.scaleY)
        assertEquals(0f, invalid.pressureProgress)
    }
}
