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
            1.51f,
            applyFloatingNavigationDrag(
                desiredPosition = 1f,
                deltaItems = 0.5f,
                itemCount = 3,
            ),
            absoluteTolerance = 0.0001f,
        )
        assertEquals(
            2.34f,
            applyFloatingNavigationDrag(
                desiredPosition = 2.17f,
                deltaItems = 0.5f,
                itemCount = 3,
            ),
            absoluteTolerance = 0.0001f,
        )
        assertEquals(
            2.5f,
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
    fun `indicator keeps the reference layer scale and signed velocity deformation`() {
        assertEquals(
            FloatingNavigationIndicatorTransform(scaleX = 1f, scaleY = 1f),
            resolveFloatingNavigationIndicatorTransform(
                interactionProgress = 0f,
                velocityItemsPerSecond = 0f,
            ),
        )

        val expandedScale = 88f / 56f
        assertEquals(
            expandedScale,
            resolveFloatingNavigationIndicatorTransform(
                interactionProgress = 1f,
                velocityItemsPerSecond = 0f,
            ).scaleX,
            absoluteTolerance = 0.0001f,
        )
        assertEquals(
            expandedScale / 0.8f,
            resolveFloatingNavigationIndicatorTransform(
                interactionProgress = 1f,
                velocityItemsPerSecond = 10f,
            ).scaleX,
            absoluteTolerance = 0.0001f,
        )
        assertEquals(
            expandedScale * 0.8f,
            resolveFloatingNavigationIndicatorTransform(
                interactionProgress = 1f,
                velocityItemsPerSecond = 10f,
            ).scaleY,
            absoluteTolerance = 0.0001f,
        )
        assertEquals(
            expandedScale / 1.2f,
            resolveFloatingNavigationIndicatorTransform(
                interactionProgress = 1f,
                velocityItemsPerSecond = -10f,
            ).scaleX,
            absoluteTolerance = 0.0001f,
        )
        assertEquals(
            expandedScale * 1.2f,
            resolveFloatingNavigationIndicatorTransform(
                interactionProgress = 1f,
                velocityItemsPerSecond = -10f,
            ).scaleY,
            absoluteTolerance = 0.0001f,
        )
    }
}
