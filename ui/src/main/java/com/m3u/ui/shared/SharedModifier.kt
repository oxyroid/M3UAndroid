package com.m3u.ui.shared

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset

fun Modifier.sharedElement(
    state: SharedState
): Modifier = composed {
    val width = state.size.width
    val height = state.size.height
    val offset = state.offset
    val shape = state.shape
    val elevation = state.elevation
    Modifier
        .offset { offset }
        .size(
            width = width,
            height = height
        )
        .graphicsLayer {
            this.shape = shape
            this.shadowElevation = elevation.value
        }
}

/**
 * An [Modifier] callback for request [SharedState] instance.
 *
 * @see Modifier.onGloballyPositioned
 * @see SharedState
 */
fun Modifier.onSharedElement(
    shape: Shape = RectangleShape,
    elevation: Dp = Dp.Unspecified,
    block: (SharedState) -> Unit
): Modifier {
    var size: DpSize = DpSize.Zero
    var offset: IntOffset = IntOffset.Zero
    return composed {
        val density = LocalDensity.current
        val scope = rememberSharedElementState(
            size = size,
            shape = shape,
            offset = offset,
            elevation = elevation
        )
        onGloballyPositioned { coordinates ->
            size = with(density) {
                val origin = coordinates.size
                DpSize(
                    width = origin.width.toDp(),
                    height = origin.height.toDp()
                )
            }
            offset = run {
                val root = coordinates.positionInRoot()
                IntOffset(
                    x = root.x.toInt(),
                    y = root.y.toInt()
                )
            }
            block(scope)
        }
    }
}