package com.m3u.smartphone.ui.navigation

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

internal data class FloatingNavigationIndicatorTransform(
    val scaleX: Float,
    val scaleY: Float,
)

internal fun resolveFloatingNavigationIndicatorPhysicalX(
    position: Float,
    itemWidthPx: Float,
    navigationWidthPx: Float,
    innerPaddingPx: Float,
    isRtl: Boolean,
): Float = if (isRtl) {
    navigationWidthPx - innerPaddingPx - (position + 1f) * itemWidthPx
} else {
    innerPaddingPx + position * itemWidthPx
}

internal fun applyFloatingNavigationDrag(
    desiredPosition: Float,
    deltaItems: Float,
    itemCount: Int,
    baseResistance: Float = 1.02f,
    overscrollResistance: Float = 0.34f,
    overscrollLimitItems: Float = 0.5f,
): Float {
    if (itemCount <= 0) return 0f

    val lastPosition = (itemCount - 1).toFloat()
    val resistance = if (desiredPosition < 0f || desiredPosition > lastPosition) {
        overscrollResistance
    } else {
        baseResistance
    }
    return (desiredPosition + deltaItems * resistance).coerceIn(
        minimumValue = -overscrollLimitItems,
        maximumValue = lastPosition + overscrollLimitItems,
    )
}

internal fun resolveFloatingNavigationReleaseTarget(
    desiredPosition: Float,
    velocityPxPerSecond: Float,
    itemWidthPx: Float,
    itemCount: Int,
    projectionTimeSeconds: Float = 0.20f,
    maximumProjectedStepCount: Int = 1,
): Int {
    if (itemCount <= 0 || itemWidthPx <= 0f) return 0

    val projectedPosition = desiredPosition +
        velocityPxPerSecond / itemWidthPx * projectionTimeSeconds
    val nearestPosition = desiredPosition.roundToInt()
    var targetPosition = projectedPosition.roundToInt()
    val maximumStepCount = maximumProjectedStepCount.coerceAtLeast(1)
    if (abs(targetPosition - nearestPosition) > maximumStepCount) {
        targetPosition = nearestPosition +
            (targetPosition - nearestPosition).sign * maximumStepCount
    }
    return targetPosition.coerceIn(0, itemCount - 1)
}

internal fun resolveFloatingNavigationIndicatorTransform(
    interactionProgress: Float,
    velocityItemsPerSecond: Float,
    expandedScale: Float = 88f / 56f,
): FloatingNavigationIndicatorTransform {
    val progress = interactionProgress.coerceIn(0f, 1f)
    val baseScale = 1f + (expandedScale - 1f) * progress
    val normalizedVelocity = velocityItemsPerSecond / 10f
    val horizontalDeformation = (normalizedVelocity * 0.75f).coerceIn(-0.2f, 0.2f)
    val verticalDeformation = (normalizedVelocity * 0.25f).coerceIn(-0.2f, 0.2f)

    return FloatingNavigationIndicatorTransform(
        scaleX = baseScale / (1f - horizontalDeformation),
        scaleY = baseScale * (1f - verticalDeformation),
    )
}
