package com.m3u.material.components.mask

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import com.m3u.material.components.OuterColumn
import com.m3u.material.ktx.thenIf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart

@Composable
fun MaskPanel(
    state: MaskState,
    isSpeedGestureEnabled: Boolean,
    onSpeedUpdated: (Float) -> Unit,
    onSpeedStart: () -> Unit,
    onSpeedEnd: () -> Unit,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Center,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val hapticFeedback = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()

    val screenWidthDp = configuration.screenWidthDp

    var isSpeeding by remember { mutableStateOf(false) }
    var offset by remember { mutableFloatStateOf(0f) }

    val speed by remember {
        derivedStateOf {
            (
                    if (!isSpeeding) 1f
                    else if (offset > 0f) 2f + offset * 3
                    else 2f + offset * 2
                    )
                .coerceIn(0.1f, 5f)
        }
    }

    if (isSpeedGestureEnabled && isSpeeding) {
        DisposableEffect(Unit) {
            val job = snapshotFlow { speed }
                .onStart { onSpeedStart() }
                .onCompletion { onSpeedEnd() }
                .onEach {
                    onSpeedUpdated(it)
                    state.sleep()
                }
                .launchIn(coroutineScope)
            onDispose {
                job.cancel()
                onSpeedUpdated(1f)
            }
        }
    }

    val onDragStart = {
        isSpeeding = true
        offset = 0f
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val onDragEndOrCancel = {
        isSpeeding = false
        offset = 0f
    }

    OuterColumn(
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        modifier = Modifier
            .fillMaxWidth(0.64f)
            .fillMaxHeight()
            .pointerInput(Unit) {
                detectTapGestures { state.toggle() }
            }
            .thenIf(isSpeedGestureEnabled) {
                Modifier.pointerInput(screenWidthDp) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onDragStart() },
                        onDragEnd = { onDragEndOrCancel() },
                        onDragCancel = { onDragEndOrCancel() },
                        onDrag = { _, dragAmount ->
                            offset = (offset + dragAmount.x / screenWidthDp * 2).coerceIn(-1f, 1f)
                        }
                    )
                }
            }
            .then(modifier),
        content = content
    )
}