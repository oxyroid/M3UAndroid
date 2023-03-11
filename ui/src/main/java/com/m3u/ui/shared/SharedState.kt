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
 * @see rememberSharedElementState
 */
@Immutable
interface SharedState {
    @get:Composable
    val size: DpSize

    @get:Composable
    val shape: Shape

    @get:Composable
    val offset: IntOffset

    @get:Composable
    val elevation: Dp
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
fun rememberSharedElementState(
    size: DpSize = DpSize(Dp.Hairline, Dp.Hairline),
    shape: Shape = RectangleShape,
    offset: IntOffset = IntOffset.Zero,
    elevation: Dp = Dp.Unspecified
): SharedState = remember(size, shape, offset, elevation) {
    SharedStateImpl(size, shape, offset, elevation)
}

fun SharedState(
    size: DpSize = DpSize(Dp.Hairline, Dp.Hairline),
    shape: Shape = RectangleShape,
    offset: IntOffset = IntOffset.Zero,
    elevation: Dp = Dp.Unspecified
): SharedState = SharedStateImpl(size, shape, offset, elevation)

/**
 * Default implementation of [SharedState]
 */
private class SharedStateImpl(
    private val innerSize: DpSize,
    private val innerShape: Shape,
    private val innerOffset: IntOffset,
    private val innerElevation: Dp
) : SharedState {
    override val size: DpSize
        @Composable get() = DpSize(
            width = innerSize.width,
            height = innerSize.height,
        )
    override val shape: Shape @Composable get() = innerShape
    override val offset: IntOffset @Composable get() = innerOffset
    override val elevation: Dp @Composable get() = innerElevation
}

/**
 * The [SharedState] for current screen.
 */
val Configuration.sharedElement: SharedState
    @Composable get() = rememberSharedElementState(
        size = with(LocalDensity.current) {
            DpSize(
                width = screenWidthDp.toDp(),
                height = screenHeightDp.toDp()
            )
        },
        shape = RectangleShape,
        offset = IntOffset.Zero
    )
