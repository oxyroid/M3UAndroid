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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.offset
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
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
    enabled: Boolean = true,
    onOffsetChanged: (Float) -> Unit = {},
    onValueChanged: (PullPanelLayoutValue) -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val useVertical = configuration.screenWidthDp < configuration.screenHeightDp
    val aspectRatio = if (useVertical) 10 / 7f else 4 / 3f
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
                    offset = if (useVertical) constraints.maxWidth * aspectRatio
                    else constraints.maxHeight / aspectRatio
                    onOffsetChanged(offset)
                    onValueChanged(PullPanelLayoutValue.EXPANDED)
                }

                PullPanelLayoutValue.COLLAPSED -> {
                    offset = 0f
                    onOffsetChanged(offset)
                    onValueChanged(PullPanelLayoutValue.COLLAPSED)
                }
            }
        }
        val currentOffset by animateFloatAsState(
            targetValue = offset,
            label = "offset"
        )
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
            val panelLayerPlaceables = subcompose(PullPanelLayoutValue.EXPANDED, panel)
                .fastMap {
                    it.measure(
                        if (useVertical) constraints
                            .copy(
                                maxHeight = currentOffset
                                    .roundToInt()
                                    .coerceAtMost(
                                        (maxWidth * aspectRatio).roundToInt()
                                    )
                            )
                        else constraints.copy(
                            maxWidth = currentOffset
                                .roundToInt()
                                .coerceAtMost(
                                    (maxHeight / aspectRatio).roundToInt()
                                )
                        )
                    )
                }

            val contentPlaceables = subcompose(PullPanelLayoutValue.COLLAPSED, content)
                .fastMap {
                    it.measure(
                        if (useVertical) constraints
                            .offset(
                                vertical = -currentOffset
                                    .roundToInt()
                                    .coerceAtMost((maxWidth * aspectRatio).roundToInt())
                            )
                        else constraints
                            .offset(
                                horizontal = -currentOffset
                                    .roundToInt()
                                    .coerceAtMost((maxHeight / aspectRatio).roundToInt())
                            )
                    )
                }

            layout(maxWidth, maxHeight) {
                contentPlaceables.fastForEach { it.placeRelative(0, 0) }
                panelLayerPlaceables.fastForEach { placeable ->
                    if (useVertical) placeable.placeRelative(
                        0,
                        contentPlaceables.maxOfOrNull { it.height } ?: 0
                    )
                    else placeable.placeRelative(
                        contentPlaceables.maxOfOrNull { it.width } ?: 0,
                        0
                    )
                }
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
