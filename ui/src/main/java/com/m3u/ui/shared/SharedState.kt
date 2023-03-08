package com.m3u.ui.shared

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset

/**
 * A Compose shared element state holder
 *
 * @see rememberSharedState
 */
@Immutable
interface SharedState {
    @get:Composable
    val size: DpSize

    @get:Composable
    val shape: Shape

    @get:Composable
    val offset: IntOffset
}

/**
 * Remember a [SharedState]
 *
 * @param size The size of this layout in the local coordinates space.
 * @param shape The shape of this layout, [RectangleShape] as default.
 * @param offset The position of this layout inside the root composable.
 * @see SharedState
 */
@Composable
fun rememberSharedState(
    size: DpSize = DpSize(Dp.Hairline, Dp.Hairline),
    shape: Shape = RectangleShape,
    offset: IntOffset = IntOffset.Zero
): SharedState = remember(size, shape, offset) {
    SharedStateImpl(size, shape, offset)
}

/**
 * Default implementation of [SharedState]
 */
private class SharedStateImpl(
    private val innerSize: DpSize,
    private val innerShape: Shape,
    private val innerOffset: IntOffset
) : SharedState {
    override val size: DpSize
        @Composable get() = DpSize(
            width = innerSize.width,
            height = innerSize.height,
        )
    override val shape: Shape @Composable get() = innerShape
    override val offset: IntOffset @Composable get() = innerOffset
}

/**
 * The [SharedState] for current screen.
 */
val Configuration.sharedState: SharedState
    @Composable get() = rememberSharedState(
        size = with(LocalDensity.current) {
            DpSize(
                width = screenWidthDp.toDp(),
                height = screenHeightDp.toDp()
            )
        },
        shape = RectangleShape,
        offset = IntOffset.Zero
    )
