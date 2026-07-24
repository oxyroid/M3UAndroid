package com.m3u.smartphone.ui.navigation

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt

internal data class FloatingNavigationIndicatorMorph(
    val scaleX: Float,
    val scaleY: Float,
    val leadOffsetFraction: Float,
    val refractionProgress: Float,
    val pressureProgress: Float,
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
    inBoundsGain: Float = 0.97f,
    edgeElasticity: Float = 0.23f,
    overscrollLimitItems: Float = 0.38f,
): Float {
    if (itemCount <= 0) return 0f

    val lastPosition = (itemCount - 1).toFloat()
    val movementGain = if (desiredPosition < 0f || desiredPosition > lastPosition) {
        edgeElasticity
    } else {
        inBoundsGain
    }
    return (desiredPosition + deltaItems * movementGain).coerceIn(
        minimumValue = -overscrollLimitItems,
        maximumValue = lastPosition + overscrollLimitItems,
    )
}

internal fun resolveFloatingNavigationReleaseTarget(
    desiredPosition: Float,
    velocityPxPerSecond: Float,
    itemWidthPx: Float,
    itemCount: Int,
    projectionTimeSeconds: Float = 0.16f,
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

/**
 * Produces a bounded, direction-aware liquid response from interaction pressure and speed.
 *
 * The exponential scale keeps both axes positive and makes the stationary press expansion
 * continuous with the additional horizontal flow stretch. The scales are intentionally
 * direction-neutral; direction only moves the optical centre slightly ahead of the finger.
 */
internal fun resolveFloatingNavigationIndicatorMorph(
    interactionProgress: Float,
    velocityItemsPerSecond: Float,
): FloatingNavigationIndicatorMorph {
    val safeInteraction = when {
        interactionProgress.isNaN() -> 0f
        interactionProgress <= 0f -> 0f
        interactionProgress >= 1f -> 1f
        else -> interactionProgress
    }
    val pressure = safeInteraction * safeInteraction * (3f - 2f * safeInteraction)
    val safeVelocity = when {
        velocityItemsPerSecond.isNaN() -> 0f
        velocityItemsPerSecond > FULL_FLOW_SPEED_ITEMS_PER_SECOND ->
            FULL_FLOW_SPEED_ITEMS_PER_SECOND
        velocityItemsPerSecond < -FULL_FLOW_SPEED_ITEMS_PER_SECOND ->
            -FULL_FLOW_SPEED_ITEMS_PER_SECOND
        else -> velocityItemsPerSecond
    }
    val normalizedSpeed = (abs(safeVelocity) / FULL_FLOW_SPEED_ITEMS_PER_SECOND)
        .coerceIn(0f, 1f)
    val flow = sqrt(normalizedSpeed)
    val radialExpansion = PRESS_EXPANSION_LOG * pressure
    val axialFlow = FLOW_STRETCH_LOG * pressure * flow
    val direction = safeVelocity.sign

    return FloatingNavigationIndicatorMorph(
        scaleX = exp(radialExpansion + axialFlow),
        scaleY = exp(radialExpansion - axialFlow * CROSS_AXIS_FLOW_RETENTION),
        leadOffsetFraction = direction * MAXIMUM_LEAD_FRACTION * pressure * flow,
        refractionProgress = (
            pressure * (BASE_REFRACTION_SHARE + FLOW_REFRACTION_SHARE * flow)
            ).coerceIn(0f, 1f),
        pressureProgress = pressure,
    )
}

private const val FULL_FLOW_SPEED_ITEMS_PER_SECOND = 6.5f
private const val PRESS_EXPANSION_LOG = 0.27f
private const val FLOW_STRETCH_LOG = 0.24f
private const val CROSS_AXIS_FLOW_RETENTION = 0.58f
private const val MAXIMUM_LEAD_FRACTION = 0.06f
private const val BASE_REFRACTION_SHARE = 0.38f
private const val FLOW_REFRACTION_SHARE = 0.62f
