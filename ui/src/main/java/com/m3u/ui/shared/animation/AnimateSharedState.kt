package com.m3u.ui.shared.animation

import androidx.compose.animation.core.AnimationVector3D
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import com.m3u.ui.model.LocalDuration
import com.m3u.ui.shared.SharedState

@Composable
fun animateSharedElementState(
    target: SharedState,
    duration: Int = LocalDuration.current.medium,
    finishedListener: () -> Unit = {}
): State<SharedState> {
    val size = target.size
    val elevation = target.elevation
    val shape = target.shape
    return animateValueAsState(
        targetValue = target,
        typeConverter = TwoWayConverter(
            convertToVector = {
                AnimationVector3D(
                    v1 = size.width.value,
                    v2 = size.height.value,
                    v3 = elevation.value,
                )
            },
            convertFromVector = {
                SharedState(
                    size = DpSize(Dp(it.v1), Dp(it.v2)),
                    elevation = Dp(it.v3),
                    shape = shape
                )
            },
        ),
        finishedListener = { finishedListener() }
    )
}
