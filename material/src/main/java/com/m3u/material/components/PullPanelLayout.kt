package com.m3u.material.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.offset
import kotlin.math.roundToInt

enum class PullPanelLayoutValue {
    EXPANDED, COLLAPSED
}

@Stable
abstract class PullPanelLayoutState {
    abstract val value: PullPanelLayoutValue
    abstract fun expand()
    abstract fun collapse()
    internal abstract val intention: PullPanelLayoutValue
}

@Composable
fun rememberPullPanelLayoutState(
    initialValue: PullPanelLayoutValue = PullPanelLayoutValue.COLLAPSED
): PullPanelLayoutState = remember(initialValue) {
    PullPanelLayoutStateImpl(initialValue)
}

@Composable
fun PullPanelLayout(
    panel: @Composable () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    state: PullPanelLayoutState = rememberPullPanelLayoutState(),
    aspectRatio: Float = 10 / 7f,
    enabled: Boolean = true,
    onOffsetChanged: (Float) -> Unit = {},
    onValueChanged: (PullPanelLayoutValue) -> Unit = {}
) {
    BackHandler(state.value == PullPanelLayoutValue.EXPANDED) {
        state.collapse()
    }
    BoxWithConstraints {
        var offset: Float by remember(state, constraints.maxHeight) {
            mutableFloatStateOf(
                when (state.value) {
                    PullPanelLayoutValue.EXPANDED -> constraints.maxWidth * aspectRatio
                    PullPanelLayoutValue.COLLAPSED -> 0f
                }
            )
        }
        LaunchedEffect(state.intention) {
            when (state.intention) {
                PullPanelLayoutValue.EXPANDED -> {
                    offset = constraints.maxWidth * aspectRatio
                    onOffsetChanged(offset)
                    if (state.value != PullPanelLayoutValue.EXPANDED) {
                        onValueChanged(PullPanelLayoutValue.EXPANDED)
                    }
                }

                PullPanelLayoutValue.COLLAPSED -> {
                    offset = 0f
                    onOffsetChanged(offset)
                    if (state.value != PullPanelLayoutValue.COLLAPSED) {
                        onValueChanged(PullPanelLayoutValue.COLLAPSED)
                    }
                }
            }
        }
        val currentOffset by animateFloatAsState(
            targetValue = offset,
            label = "offset"
        )
        LaunchedEffect(enabled) {
            if (!enabled) {
                state.collapse()
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
                        offset = if (offset <= constraints.maxWidth * aspectRatio / 2) {

                            if (state.value != PullPanelLayoutValue.COLLAPSED) {
                                onValueChanged(PullPanelLayoutValue.COLLAPSED)
                            }
                            state.collapse()
                            0f
                        } else {
                            if (state.value != PullPanelLayoutValue.EXPANDED) {
                                onValueChanged(PullPanelLayoutValue.EXPANDED)
                            }
                            state.expand()
                            constraints.maxWidth * aspectRatio
                        }.also(onOffsetChanged)
                    }
                )
        ) { constraints ->
            val maxHeight = constraints.maxHeight
            val maxWidth = constraints.maxWidth
            val panelLayerPlaceable = subcompose(PullPanelLayoutValue.EXPANDED, panel)
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

            val contentPlaceable = subcompose(PullPanelLayoutValue.COLLAPSED, content)
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
}

private class PullPanelLayoutStateImpl(
    initialValue: PullPanelLayoutValue = PullPanelLayoutValue.COLLAPSED
) : PullPanelLayoutState() {
    override var value: PullPanelLayoutValue by mutableStateOf(initialValue)
    override fun expand() {
        value = PullPanelLayoutValue.EXPANDED
        intention = PullPanelLayoutValue.EXPANDED
    }

    override fun collapse() {
        value = PullPanelLayoutValue.COLLAPSED
        intention = PullPanelLayoutValue.COLLAPSED
    }

    override var intention: PullPanelLayoutValue by mutableStateOf(initialValue)
}

