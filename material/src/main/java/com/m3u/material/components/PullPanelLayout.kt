package com.m3u.material.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.offset
import kotlin.math.roundToInt

enum class PullPanelLayoutState {
    EXPANDED, COLLAPSED
}

@Composable
fun PullPanelLayout(
    panel: @Composable () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    initialOffset: Float = 0f,
    aspectRatio: Float = 16 / 10f,
    enabled: Boolean = true,
    onOffsetChanged: (Float) -> Unit = {},
    onStateChanged: (PullPanelLayoutState) -> Unit = {}
) {
    var offset: Float by remember(initialOffset) { mutableFloatStateOf(initialOffset) }
    val currentOffset by animateFloatAsState(
        targetValue = offset,
        label = "offset"
    )
    var savedMaxHeight by remember { mutableIntStateOf(0) }
    var savedMaxWidth by remember { mutableIntStateOf(0) }
    LaunchedEffect(enabled) {
        if (!enabled) {
            offset = 0f
            onOffsetChanged(0f)
            onStateChanged(PullPanelLayoutState.COLLAPSED)
        }
    }
    SubcomposeLayout(
        modifier
            .draggable(
                orientation = Orientation.Vertical,
                enabled = enabled,
                state = rememberDraggableState { delta ->
                    offset = (offset - delta)
                        .coerceAtLeast(0f)
                        .also(onOffsetChanged)
                },
                onDragStopped = {
                    offset = if (offset <= savedMaxWidth * aspectRatio / 2) {
                        onStateChanged(PullPanelLayoutState.COLLAPSED)
                        0f
                    } else {
                        onStateChanged(PullPanelLayoutState.EXPANDED)
                        savedMaxWidth * aspectRatio
                    }.also(onOffsetChanged)
                }
            )
    ) { constraints ->
        val maxHeight = constraints.maxHeight
        val maxWidth = constraints.maxWidth
        savedMaxHeight = maxHeight
        savedMaxWidth = maxWidth
        val panelLayerPlaceable = subcompose(true, panel)
            .first()
            .measure(
                constraints
                    .copy(
                        maxHeight = currentOffset
                            .roundToInt()
                            .coerceAtMost(
                                (maxWidth * aspectRatio).roundToInt()
                            )
                    )
            )

        val contentPlaceable = subcompose(Unit, content)
            .first()
            .measure(
                constraints
                    .offset(
                        vertical = -currentOffset
                            .roundToInt()
                            .coerceAtMost((maxWidth * aspectRatio).roundToInt())
                    )
            )

        layout(maxWidth, maxHeight) {
            contentPlaceable.placeRelative(0, 0)
            panelLayerPlaceable.placeRelative(0, contentPlaceable.height)
        }
    }
}
