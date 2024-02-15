package com.m3u.features.foryou.components.sheet

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.m3u.data.television.http.endpoint.SayHello
import com.m3u.data.television.model.RemoteDirection
import com.m3u.material.model.LocalSpacing

@Composable
@InternalComposeApi
internal fun ColumnScope.RemoteControlSheetContent(
    television: SayHello.TelevisionInfo,
    onRemoteDirection: (RemoteDirection) -> Unit,
    onDisconnect: () -> Unit
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        Text(
            text = television.model,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )

        RemoteDirectionCircleButtons(
            onRemoteDirection = onRemoteDirection
        )

        TextButton(
            onClick = onDisconnect,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Text("DISCONNECT")
        }
    }
}

@Composable
private fun RemoteDirectionCircleButtons(
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

    val containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
    val contentColor = MaterialTheme.colorScheme.onSurface
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
                .then(modifier)
        ) {
            val radius = size.minDimension / 2

            if (currentCenter.isUnspecified) {
                targetDirection = null
            } else {
                val merged = (currentCenter - center)
                val tan = merged.x / merged.y
                //  +1(--)   0   -1(+-)
                //  gigantic     gigantic
                //  -1(-+)   0   +1(++)
                targetDirection = when {
                    (currentCenter - center).getDistance() < radius * 0.65f -> null
                    currentCenter == center -> null
                    merged.x < 0f && (tan > 1f || tan < -1f) -> RemoteDirection.LEFT
                    merged.x > 0f && (tan > 1f || tan < -1f) -> RemoteDirection.RIGHT
                    merged.y < 0f && tan in -1f..1f -> RemoteDirection.UP
                    merged.y > 0f && tan in -1f..1f -> RemoteDirection.DOWN
                    else -> null
                }
            }


            when {
                currentCenter == Offset.Unspecified -> {
                    triggered = false
                    currentCenter = center
                }

                !triggered && ((currentCenter - center).getDistance() > (radius * 0.65f)) -> {
                    triggered = true
                    targetDirection?.let { onRemoteDirection(it) }
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
            drawCircle(
                color = containerColor,
                radius = radius
            )
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
                tint = contentColor.copy(0.45f),
                modifier = Modifier.matchParentSize()
            )
        }
    }
}