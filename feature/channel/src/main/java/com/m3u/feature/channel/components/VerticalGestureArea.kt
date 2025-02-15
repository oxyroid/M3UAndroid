package com.m3u.feature.channel.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.m3u.feature.channel.ChannelMaskUtils.detectVerticalGesture
import com.m3u.material.ktx.tv
import com.m3u.material.ktx.thenIf

@Composable
internal fun VerticalGestureArea(
    percent: Float,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDrag: (percent: Float) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    time: Float = 0.65f,
    enabled: Boolean = true
) {
    val currentPercent by rememberUpdatedState(percent)
    BoxWithConstraints(modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .thenIf(enabled) {
                    Modifier.detectVerticalGesture(
                        time = time,
                        onDragStart = onDragStart,
                        onDragEnd = onDragEnd,
                        onVerticalDrag = { deltaPixel ->
                            onDrag(
                                (currentPercent - deltaPixel / this@BoxWithConstraints.maxHeight.value)
                                    .coerceIn(0f..1f)
                            )
                        }
                    )
                }
                .clickable(
                    onClick = onClick,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                )
        )
    }
}
