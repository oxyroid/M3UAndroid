package com.m3u.ui.shared

import android.annotation.SuppressLint
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import com.m3u.ui.shared.material.SharedSurface

/**
 * Providing an animated modifier to the content with the changing of [SharedState].
 * @param state Target [SharedState].
 * @param elevation Layout elevation.
 * @param content The content which holding an animated modifier.
 * @see SharedState
 * @see SharedSurface
 */
@Composable
fun AnimatedModifier(
    state: SharedState = LocalConfiguration.current.sharedState,
    elevation: Dp = Dp.Unspecified,
    @SuppressLint("ModifierParameter")
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit
) {
    val width by animateDpAsState(state.size.width)
    val height by animateDpAsState(state.size.height)
    val offset by animateIntOffsetAsState(state.offset)
    val shape = state.shape
    val combinedModifier = Modifier
        .offset { offset }
        .size(
            width = width,
            height = height
        )
        .graphicsLayer {
            this.shape = shape
            this.shadowElevation = elevation.value
        }
        .then(modifier)
    content(combinedModifier)
}

/**
 * An [Modifier] callback for request [SharedState] instance.
 *
 * @see Modifier.onGloballyPositioned
 * @see SharedState
 */
fun Modifier.onSharedState(
    shape: Shape = RectangleShape,
    block: (SharedState) -> Unit
): Modifier {
    var size: DpSize = DpSize.Zero
    var offset: IntOffset = IntOffset.Zero
    return composed {
        val density = LocalDensity.current
        val scope = rememberSharedState(
            size = size,
            shape = shape,
            offset = offset
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