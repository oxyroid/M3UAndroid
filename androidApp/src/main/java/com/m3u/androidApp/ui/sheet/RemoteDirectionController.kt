package com.m3u.androidApp.ui.sheet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.m3u.data.television.model.RemoteDirection

@Composable
internal fun RemoteDirectionController(
    onRemoteDirection: (RemoteDirection) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = Color.White,
    arrowColor: Color = contentColor.copy(0.65f),
    handleScaleZoom: Float = 0.65f,
    handleSafeThreshold: Float = 0.65f,
    hapticFeedbackType: HapticFeedbackType? = HapticFeedbackType.LongPress
) {
    LaunchedEffect(handleScaleZoom, handleSafeThreshold) {
        check(handleScaleZoom in 0f..1f) { "handleScaleZoom should in range [0f, 1f]" }
        check(handleSafeThreshold > 0f && handleSafeThreshold < 1f) { "handleSafeThreshold should in range (0f, 1f)" }
    }
    var handled: Boolean by remember { mutableStateOf(false) }
    val currentHandleZoom: Float by animateFloatAsState(
        targetValue = if (handled) handleScaleZoom else 1f,
        label = "current-handle-zoom"
    )
    val currentContainerColor by animateColorAsState(
        targetValue = containerColor.copy(alpha = if (handled) 0.45f else 1f),
        label = "current-container-color"
    )
    var handlePosition: Offset by remember { mutableStateOf(Offset.Unspecified) }
    val draggable2DState = rememberDraggable2DState { offset ->
        handlePosition += offset
    }
    var handleDirection: RemoteDirection? by remember { mutableStateOf(null) }
    var currentDashboardRotationX: Float by remember { mutableFloatStateOf(0f) }
    var currentDashboardRotationY: Float by remember { mutableFloatStateOf(0f) }

    val hapticFeedback = LocalHapticFeedback.current

    Box(contentAlignment = Alignment.Center) {
        // dashboard
        Canvas(
            Modifier
                .wrapContentSize(Alignment.Center)
                .requiredSize(RemoteDirectionControllerSize)
                .draggable2D(
                    state = draggable2DState,
                    onDragStopped = {
                        handlePosition = Offset.Unspecified
                    }
                )
                .graphicsLayer {
                    rotationX = currentDashboardRotationX
                    rotationY = currentDashboardRotationY
                }
                .then(modifier)
        ) {
            val radius = size.minDimension / 2
            val safeRadius = radius * handleSafeThreshold

            if (handlePosition.isUnspecified) {
                handleDirection = null
                currentDashboardRotationX = 0f
                currentDashboardRotationY = 0f
                handled = false
                handlePosition = center
            } else {
                val merged = handlePosition - center
                val tan = merged.x / merged.y
                //  +1(--)   0   -1(+-)
                //  gigantic     gigantic
                //  -1(-+)   0   +1(++)
                handleDirection = when {
                    merged.getDistance() < safeRadius -> null
                    handlePosition == center -> null
                    merged.x < 0f && (tan > 1f || tan < -1f) -> RemoteDirection.LEFT
                    merged.x > 0f && (tan > 1f || tan < -1f) -> RemoteDirection.RIGHT
                    merged.y < 0f && tan in -1f..1f -> RemoteDirection.UP
                    merged.y > 0f && tan in -1f..1f -> RemoteDirection.DOWN
                    else -> null
                }
                val xNeg = if (merged.y > 0) -1f else 1f
                val yNeg = if (merged.x < 0) -1f else 1f
                currentDashboardRotationX = merged.copy(x = 0f).getDistance() / radius * 15 * xNeg
                currentDashboardRotationY = merged.copy(y = 0f).getDistance() / radius * 15 * yNeg

                if (!handled && merged.getDistance() > safeRadius) {
                    handled = true
                    handleDirection?.let {
                        onRemoteDirection(it)
                        hapticFeedbackType?.let { type -> hapticFeedback.performHapticFeedback(type) }
                    }
                }
            }

            drawCircle(
                color = currentContainerColor,
                radius = radius
            )
        }
        // handle
        Canvas(Modifier.matchParentSize()) {
            val radius = size.minDimension / 2
            drawCircle(
                color = contentColor.copy(currentHandleZoom),
                radius = radius / 4 * currentHandleZoom,
                center = handlePosition
            )
        }
        val icon = when (handleDirection) {
            RemoteDirection.LEFT -> Icons.AutoMirrored.Rounded.KeyboardArrowLeft
            RemoteDirection.RIGHT -> Icons.AutoMirrored.Rounded.KeyboardArrowRight
            RemoteDirection.UP -> Icons.Rounded.KeyboardArrowUp
            RemoteDirection.DOWN -> Icons.Rounded.KeyboardArrowDown
            else -> null
        }
        // icon
        AnimatedVisibility(
            visible = icon != null,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    rotationX = currentDashboardRotationX
                    rotationY = currentDashboardRotationY
                }
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = handleDirection?.name,
                    tint = arrowColor,
                    modifier = Modifier.matchParentSize()
                )
            }
        }
    }
}

private val RemoteDirectionControllerSize = 256.dp
