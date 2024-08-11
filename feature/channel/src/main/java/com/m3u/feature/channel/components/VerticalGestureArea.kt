package com.m3u.feature.channel.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.feature.channel.ChannelMaskUtils.detectVerticalGesture
import com.m3u.material.ktx.tv
import com.m3u.material.ktx.thenIf

@Composable
internal fun VerticalGestureArea(
    percent: Float,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDrag: (percent: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val preferences = hiltPreferences()
    val tv = tv()
    val currentPercent by rememberUpdatedState(percent)
    BoxWithConstraints(modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .thenIf(!tv && preferences.brightnessGesture) {
                    Modifier.detectVerticalGesture(
                        time = 0.65f,
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
        )
    }
}
