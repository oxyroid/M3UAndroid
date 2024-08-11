package com.m3u.androidApp.ui.sheet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import com.m3u.material.components.Icon
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
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.geometry.takeOrElse
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.m3u.data.tv.model.RemoteDirection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun RemoteDirectionController(
    onRemoteDirection: (RemoteDirection) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    handleScaleZoom: Float = 0.25f,
    pressedScaleZoom: Float = 0.56f,
    safeDragThreshold: Float = 0.42f,
    safePressThreshold: Float = 0.35f,
    hapticFeedbackType: HapticFeedbackType? = HapticFeedbackType.LongPress
) {
    LaunchedEffect(handleScaleZoom, safeDragThreshold, safePressThreshold) {
        check(handleScaleZoom in 0f..1f) { "handleScaleZoom should in range [0f, 1f]" }
        check(safeDragThreshold > 0f && safeDragThreshold < 1f) { "safeDragThreshold should in range (0f, 1f)" }
        check(safePressThreshold in 0f..1f) { "safePressThreshold should in range [0f, 1f]" }
    }
    var handled: Boolean by remember { mutableStateOf(false) }
    var pressed: Boolean by remember { mutableStateOf(false) }
    val currentHandleZoom: Float by animateFloatAsState(
        targetValue = when {
            handled -> handleScaleZoom
            pressed -> pressedScaleZoom
            else -> 0.35f
        },
        animationSpec = tween(400),
        label = "current-handle-zoom"
    )
    val currentHandleAlpha: Float by animateFloatAsState(
        targetValue = when {
            handled -> 0.78f
            else -> 0.35f
        },
        label = "current-handle-zoom"
    )
    val currentContainerColor by animateColorAsState(
        targetValue = containerColor.copy(alpha = if (handled) 0.45f else 1f),
        label = "current-container-color"
    )
    var draggedPosition: Offset by remember { mutableStateOf(Offset.Unspecified) }
    var pressedPosition: Offset by remember { mutableStateOf(Offset.Unspecified) }
    val draggable2DState = rememberDraggable2DState { offset ->
        draggedPosition += offset
    }
    var direction: RemoteDirection? by remember { mutableStateOf(null) }
    var currentDashboardRotationX: Float by remember { mutableFloatStateOf(0f) }
    var currentDashboardRotationY: Float by remember { mutableFloatStateOf(0f) }

    val hapticFeedback = LocalHapticFeedback.current

    val interactionSource = remember { MutableInteractionSource() }

    val currentPosition by animateOffsetAsState(
        targetValue = pressedPosition.takeOrElse { draggedPosition.takeOrElse { Offset.Zero } },
        label = "current-position"
    )

    LaunchedEffect(Unit) {
        interactionSource
            .interactions
            .onEach { interaction ->
                when (interaction) {
                    is PressInteraction -> {
                        when (interaction) {
                            is PressInteraction.Press -> {
                                delay(150.milliseconds)
                                pressed = true
                                pressedPosition = interaction.pressPosition
                            }

                            is PressInteraction.Cancel -> {
                                pressed = false
                                pressedPosition = Offset.Unspecified
                            }

                            is PressInteraction.Release -> {
                                pressed = false
                                pressedPosition = Offset.Unspecified
                                direction?.let { onRemoteDirection(it) }
                                hapticFeedbackType?.let { type ->
                                    hapticFeedback.performHapticFeedback(type)
                                }
                            }
                        }
                    }

                    else -> {}
                }
            }
            .launchIn(this)
    }

    Box(contentAlignment = Alignment.Center) {
        // dashboard
        Canvas(
            Modifier
                .wrapContentSize(Alignment.Center)
                .requiredSize(RemoteDirectionControllerSize)
                .draggable2D(
                    state = draggable2DState,
                    enabled = !pressed,
                    onDragStopped = {
                        draggedPosition = Offset.Unspecified
                    }
                )
                .clickable(
                    interactionSource = interactionSource,
                    enabled = !handled,
                    indication = null,
                    onClick = {}
                )
                .graphicsLayer {
                    rotationX = currentDashboardRotationX
                    rotationY = currentDashboardRotationY
                }
                .then(modifier)
        ) {
            val radius = size.minDimension / 2
            val safeDragRadius = radius * safeDragThreshold
            val safePressRadius = radius * safePressThreshold

            when {
                pressedPosition.isSpecified -> {
                    val merged = pressedPosition - center
                    val tan = merged.x / merged.y
                    //  +1(--)   0   -1(+-)
                    //  gigantic     gigantic
                    //  -1(-+)   0   +1(++)
                    direction = when {
                        merged.getDistance() < safePressRadius -> RemoteDirection.ENTER
                        merged.x < 0f && (tan > 1f || tan < -1f) -> RemoteDirection.LEFT
                        merged.x > 0f && (tan > 1f || tan < -1f) -> RemoteDirection.RIGHT
                        merged.y < 0f && tan in -1f..1f -> RemoteDirection.UP
                        merged.y > 0f && tan in -1f..1f -> RemoteDirection.DOWN
                        else -> null
                    }
                }

                draggedPosition.isSpecified -> {
                    val merged = draggedPosition - center
                    val tan = merged.x / merged.y
                    //  +1(--)   0   -1(+-)
                    //  gigantic     gigantic
                    //  -1(-+)   0   +1(++)
                    direction = when {
                        merged.getDistance() < safeDragRadius -> null
                        merged.x < 0f && (tan > 1f || tan < -1f) -> RemoteDirection.LEFT
                        merged.x > 0f && (tan > 1f || tan < -1f) -> RemoteDirection.RIGHT
                        merged.y < 0f && tan in -1f..1f -> RemoteDirection.UP
                        merged.y > 0f && tan in -1f..1f -> RemoteDirection.DOWN
                        else -> null
                    }
                    val xNeg = if (merged.y > 0) -1f else 1f
                    val yNeg = if (merged.x < 0) -1f else 1f
                    currentDashboardRotationX = merged.copy(x = 0f)
                        .getDistance() / radius * 15 * xNeg
                    currentDashboardRotationY = merged.copy(y = 0f)
                        .getDistance() / radius * 15 * yNeg

                    if (!handled && merged.getDistance() > safeDragRadius) {
                        handled = true
                        direction?.let {
                            onRemoteDirection(it)
                            hapticFeedbackType?.let { type ->
                                hapticFeedback.performHapticFeedback(type)
                            }
                        }
                    }
                }

                else -> {
                    when {
                        pressedPosition.isUnspecified && draggedPosition.isUnspecified -> {
                            direction = null
                            currentDashboardRotationX = 0f
                            currentDashboardRotationY = 0f
                            pressed = false
                            handled = false
                            pressedPosition = center
                            draggedPosition = center
                        }

                        draggedPosition.isUnspecified -> {
                            handled = false
                            draggedPosition = center
                        }

                        pressedPosition.isUnspecified -> {
                            pressed = false
                            pressedPosition = center
                        }
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
                color = contentColor.copy(currentHandleAlpha),
                radius = radius * currentHandleZoom,
                center = currentPosition.takeOrElse { center }
            )
        }
        val icon = when (direction) {
            RemoteDirection.LEFT -> Icons.AutoMirrored.Rounded.KeyboardArrowLeft
            RemoteDirection.RIGHT -> Icons.AutoMirrored.Rounded.KeyboardArrowRight
            RemoteDirection.UP -> Icons.Rounded.KeyboardArrowUp
            RemoteDirection.DOWN -> Icons.Rounded.KeyboardArrowDown
            else -> null
        }
        // icon
        AnimatedVisibility(
            visible = handled || pressed,
            enter = fadeIn() + scaleIn(initialScale = 0.65f),
            exit = fadeOut() + scaleOut(targetScale = 0.65f),
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
                    contentDescription = direction?.name,
                    tint = contentColor,
                    modifier = Modifier.matchParentSize()
                )
            }
        }
    }
}

private val RemoteDirectionControllerSize = 256.dp
