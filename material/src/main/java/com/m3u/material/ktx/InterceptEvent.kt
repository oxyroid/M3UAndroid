package com.m3u.material.ktx

import android.view.KeyEvent
import androidx.annotation.IntDef
import androidx.compose.foundation.focusable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent

fun Modifier.interceptVolumeEvent(
    minDuration: Long = 200L,
    onEvent: (@VolumeEvent Int) -> Unit
): Modifier = composed {
    if (minDuration < 0L) error("Modifier.interceptVolumeEvent: minDuration cannot less than 0.")
    val requester = remember { FocusRequester() }
    var lastKeyTime by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        requester.requestFocus()
    }
    onKeyEvent { event ->
        val currentTimeMillis = System.currentTimeMillis()
        when (event.nativeKeyEvent.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (currentTimeMillis - lastKeyTime >= minDuration) {
                    onEvent(KeyEvent.KEYCODE_VOLUME_UP)
                    lastKeyTime = currentTimeMillis
                }
                true
            }

            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (currentTimeMillis - lastKeyTime >= minDuration) {
                    onEvent(KeyEvent.KEYCODE_VOLUME_DOWN)
                    lastKeyTime = currentTimeMillis
                }
                true
            }

            else -> false
        }
    }
        .focusRequester(requester)
        .focusable()
}

@Target(AnnotationTarget.TYPE)
@IntDef(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN)
annotation class VolumeEvent
