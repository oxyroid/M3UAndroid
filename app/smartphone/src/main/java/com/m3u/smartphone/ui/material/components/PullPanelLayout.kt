package com.m3u.smartphone.ui.material.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalWindowInfo
import com.m3u.core.foundation.ui.thenIf
import kotlin.math.roundToInt

@Composable
fun PullPanelLayout(
    panel: @Composable () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    state: PullPanelLayoutState = rememberPullPanelLayoutState(),
    enabled: Boolean = true,
    useVertical: Boolean = PullPanelLayoutDefaults.UseVertical,
    aspectRatio: Float = PullPanelLayoutDefaults.getAspectRatio(useVertical),
) {
    val impl = state as PullPanelLayoutStateImpl
    val fraction by animateFloatAsState(impl.fraction)
    Layout(
        content = {
            Box { content() }
            Box { panel() }
        },
        modifier = Modifier
            .thenIf(enabled && useVertical) {
                Modifier.pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { impl.isDragging = true },
                        onVerticalDrag = { _, delta ->
                            val before = impl.fraction
                            impl.fraction = (before + (-delta) * 2 / size.height).coerceIn(0f, 1f)
                            val after = impl.fraction
                            (after - before) / ((-delta) * 2 / size.height) * delta
                        },
                        onDragEnd = { impl.isDragging = false },
                        onDragCancel = { impl.isDragging = false }
                    )
                }
            }
            .then(modifier)
    ) { measurables, constraints ->
        val maxWidth = constraints.maxWidth
        val maxHeight = constraints.maxHeight
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val contentDistance = if (useVertical) maxHeight - maxWidth / aspectRatio
        else maxWidth - maxHeight * aspectRatio
        val contentPlaceable = measurables[0].measure(
            if (useVertical) looseConstraints.copy(maxHeight = maxHeight - (contentDistance * fraction).roundToInt())
            else looseConstraints.copy(maxWidth = maxWidth - (contentDistance * fraction).roundToInt())
        )
        val panelPlaceable = measurables[1].measure(
            if (useVertical) looseConstraints.copy(maxHeight = maxHeight - contentPlaceable.height)
            else looseConstraints.copy(maxWidth = maxWidth - contentPlaceable.width)
        )
        layout(maxWidth, maxHeight) {
            if (useVertical) {
                contentPlaceable.placeRelative(0, 0)
                panelPlaceable.placeRelative(0, contentPlaceable.height)
            } else {
                contentPlaceable.placeRelative(0, 0)
                panelPlaceable.placeRelative(contentPlaceable.width, 0)
            }
        }
    }
}

enum class PullPanelLayoutValue {
    EXPANDED, COLLAPSED
}

@Stable
sealed class PullPanelLayoutState {
    abstract val value: PullPanelLayoutValue
    abstract val fraction: Float
    abstract fun expand()
    abstract fun collapse()
}

val PullPanelLayoutState.isExpanded: Boolean
    inline get() = value == PullPanelLayoutValue.EXPANDED

@Composable
fun rememberPullPanelLayoutState(
    fraction: Float = 0f
): PullPanelLayoutState = remember(fraction) {
    PullPanelLayoutStateImpl(fraction)
}

object PullPanelLayoutDefaults {
    val UseVertical: Boolean
        @Composable
        get() {
            val windowInfo = LocalWindowInfo.current
            return with(windowInfo.containerSize) { width < height }
        }

    val AspectRatio: Float
        @Composable
        get() {
            return getAspectRatio(UseVertical)
        }

    fun getAspectRatio(useVertical: Boolean): Float = if (useVertical) 10 / 7f else 4 / 3f
}

@Stable
private class PullPanelLayoutStateImpl(
    fraction: Float = 0f
) : PullPanelLayoutState() {
    override var fraction: Float by mutableFloatStateOf(fraction)
    override var value: PullPanelLayoutValue by mutableStateOf(
        if (fraction < 0.5f) PullPanelLayoutValue.COLLAPSED
        else PullPanelLayoutValue.EXPANDED
    )

    override fun expand() {
        fraction = 1f
        value = PullPanelLayoutValue.EXPANDED
    }

    override fun collapse() {
        fraction = 0f
        value = PullPanelLayoutValue.COLLAPSED
    }

    private var _isDragging: Boolean by mutableStateOf(false)
    var isDragging: Boolean = false
        get() = _isDragging
        set(newValue) {
            if (!newValue) {
                if (fraction < 0.5f) collapse()
                else expand()
            }
            field = newValue
        }
}
