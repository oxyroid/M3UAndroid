package com.m3u.smartphone.ui.common.connect

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
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.takeOrElse
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
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
    var handled by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val currentHandleZoom by animateFloatAsState(
        targetValue = when {
            handled -> handleScaleZoom
            pressed -> pressedScaleZoom
            else -> 0.35f
        },
        animationSpec = tween(400),
        label = "current-handle-zoom"
    )
    val currentHandleAlpha by animateFloatAsState(
        targetValue = when {
            handled -> 0.78f
            else -> 0.35f
        },
        label = "current-handle-alpha"
    )
    val currentContainerColor by animateColorAsState(
        targetValue = containerColor.copy(alpha = if (handled) 0.45f else 1f),
        label = "current-container-color"
    )
    var controllerCenter by remember { mutableStateOf(Offset.Zero) }
    var controllerRadius by remember { mutableFloatStateOf(0f) }
    var draggedPosition by remember { mutableStateOf(Offset.Unspecified) }
    var pressedPosition by remember { mutableStateOf(Offset.Unspecified) }
    var direction: RemoteDirection? by remember { mutableStateOf(null) }
    var currentDashboardRotationX by remember { mutableFloatStateOf(0f) }
    var currentDashboardRotationY by remember { mutableFloatStateOf(0f) }
    val hapticFeedback = LocalHapticFeedback.current
    val draggable2DState = rememberDraggable2DState { offset ->
        if (controllerRadius == 0f) return@rememberDraggable2DState

        val nextPosition = draggedPosition.takeOrElse { controllerCenter } + offset
        val merged = nextPosition - controllerCenter
        val nextDirection = merged.remoteDirection(
            thresholdRadius = controllerRadius * safeDragThreshold,
            centerDirection = null
        )
        val (rotationX, rotationY) = merged.dashboardRotation(controllerRadius)

        draggedPosition = nextPosition
        direction = nextDirection
        currentDashboardRotationX = rotationX
        currentDashboardRotationY = rotationY

        if (!handled && merged.getDistance() > controllerRadius * safeDragThreshold) {
            handled = true
            nextDirection?.let {
                onRemoteDirection(it)
                hapticFeedbackType?.let { type ->
                    hapticFeedback.performHapticFeedback(type)
                }
            }
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val currentPosition by animateOffsetAsState(
        targetValue = pressedPosition.takeOrElse { draggedPosition.takeOrElse { Offset.Zero } },
        label = "current-position"
    )

    LaunchedEffect(Unit) {
        interactionSource
            .interactions
            .onEach { interaction ->
                if (interaction is PressInteraction) {
                    when (interaction) {
                        is PressInteraction.Press -> {
                            delay(150.milliseconds)
                            val nextDirection = interaction.pressPosition
                                .minus(controllerCenter)
                                .remoteDirection(
                                    thresholdRadius = controllerRadius * safePressThreshold,
                                    centerDirection = RemoteDirection.ENTER
                                )

                            pressed = true
                            pressedPosition = interaction.pressPosition
                            direction = nextDirection
                        }

                        is PressInteraction.Cancel -> {
                            pressed = false
                            pressedPosition = Offset.Unspecified
                            direction = null
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
            }
            .launchIn(this)
    }

    Box(contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .wrapContentSize(Alignment.Center)
                .requiredSize(RemoteDirectionControllerSize)
                .draggable2D(
                    state = draggable2DState,
                    enabled = !pressed,
                    onDragStopped = {
                        handled = false
                        draggedPosition = Offset.Unspecified
                        direction = null
                        currentDashboardRotationX = 0f
                        currentDashboardRotationY = 0f
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
                .onSizeChanged { size ->
                    controllerCenter = Offset(size.width / 2f, size.height / 2f)
                    controllerRadius = minOf(size.width, size.height) / 2f
                }
                .then(modifier)
        ) {
            val radius = size.minDimension / 2
            drawCircle(
                color = currentContainerColor,
                radius = radius
            )
        }
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

private fun Offset.remoteDirection(
    thresholdRadius: Float,
    centerDirection: RemoteDirection?
): RemoteDirection? {
    if (getDistance() < thresholdRadius) return centerDirection

    val tan = x / y
    return when {
        x < 0f && (tan > 1f || tan < -1f) -> RemoteDirection.LEFT
        x > 0f && (tan > 1f || tan < -1f) -> RemoteDirection.RIGHT
        y < 0f && tan in -1f..1f -> RemoteDirection.UP
        y > 0f && tan in -1f..1f -> RemoteDirection.DOWN
        else -> null
    }
}

private fun Offset.dashboardRotation(radius: Float): Pair<Float, Float> {
    if (radius == 0f) return 0f to 0f

    val xNeg = if (y > 0) -1f else 1f
    val yNeg = if (x < 0) -1f else 1f
    val rotationX = copy(x = 0f).getDistance() / radius * 15 * xNeg
    val rotationY = copy(y = 0f).getDistance() / radius * 15 * yNeg
    return rotationX to rotationY
}
