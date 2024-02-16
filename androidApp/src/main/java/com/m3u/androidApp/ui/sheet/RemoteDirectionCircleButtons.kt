package com.m3u.androidApp.ui.sheet

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
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
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
import com.m3u.material.model.LocalHazeState
import dev.chrisbanes.haze.hazeChild

@Composable
internal fun RemoteDirectionCircleButtons(
    onRemoteDirection: (RemoteDirection) -> Unit,
    modifier: Modifier = Modifier
) {
    var triggered: Boolean by remember { mutableStateOf(false) }
    val triggeredZoom: Float by animateFloatAsState(
        targetValue = if (triggered) 0.65f else 1f,
        label = "triggered-zoom"
    )
    var currentCenter: Offset by remember { mutableStateOf(Offset.Unspecified) }
    val draggable2DState = rememberDraggable2DState { offset ->
        currentCenter += offset
    }
    var targetDirection: RemoteDirection? by remember { mutableStateOf(null) }
    var targetRotationX: Float by remember { mutableFloatStateOf(0f) }
    var targetRotationY: Float by remember { mutableFloatStateOf(0f) }

    val containerColor = MaterialTheme.colorScheme.surfaceVariant
    val contentColor = Color.White
    val hapticFeedback = LocalHapticFeedback.current

    Box(contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .wrapContentSize(Alignment.Center)
                .requiredSize(256.dp)
                .draggable2D(
                    state = draggable2DState,
                    onDragStopped = {
                        currentCenter = Offset.Unspecified
                    }
                )
                .graphicsLayer {
                    rotationX = targetRotationX
                    rotationY = targetRotationY
                }
                .then(modifier)
        ) {
            val radius = size.minDimension / 2

            if (currentCenter.isUnspecified) {
                targetDirection = null
                targetRotationX = 0f
                targetRotationY = 0f
            } else {
                val merged = currentCenter - center
                val tan = merged.x / merged.y
                //  +1(--)   0   -1(+-)
                //  gigantic     gigantic
                //  -1(-+)   0   +1(++)
                targetDirection = when {
                    merged.getDistance() < radius * 0.65f -> null
                    currentCenter == center -> null
                    merged.x < 0f && (tan > 1f || tan < -1f) -> RemoteDirection.LEFT
                    merged.x > 0f && (tan > 1f || tan < -1f) -> RemoteDirection.RIGHT
                    merged.y < 0f && tan in -1f..1f -> RemoteDirection.UP
                    merged.y > 0f && tan in -1f..1f -> RemoteDirection.DOWN
                    else -> null
                }
                targetRotationX = merged.copy(x = 0f).getDistance() / radius * 15 * if (merged.y < 0) -1f else 1f
                targetRotationY = merged.copy(y = 0f).getDistance() / radius * 15 * if (merged.x > 0) -1f else 1f
            }


            when {
                currentCenter == Offset.Unspecified -> {
                    triggered = false
                    currentCenter = center
                }

                !triggered && (currentCenter - center).getDistance() > (radius * 0.65f) -> {
                    triggered = true
                    targetDirection?.let { onRemoteDirection(it) }
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
            drawCircle(
                color = containerColor,
                radius = radius
            )
        }
        Canvas(
            Modifier.matchParentSize()
        ) {
            val radius = size.minDimension / 2
            drawCircle(
                color = contentColor,
                radius = radius / 4 * triggeredZoom,
                center = currentCenter
            )
        }
        val icon = when (targetDirection) {
            RemoteDirection.LEFT -> Icons.AutoMirrored.Rounded.KeyboardArrowLeft
            RemoteDirection.RIGHT -> Icons.AutoMirrored.Rounded.KeyboardArrowRight
            RemoteDirection.UP -> Icons.Rounded.KeyboardArrowUp
            RemoteDirection.DOWN -> Icons.Rounded.KeyboardArrowDown
            else -> null
        }
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = targetDirection?.name,
                tint = contentColor.copy(0.65f),
                modifier = Modifier.matchParentSize()
            )
        }
    }
}