package com.m3u.material

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

val LocalM3UHapticFeedback = staticCompositionLocalOf<M3UHapticFeedback> {
    throw RuntimeException("no m3u haptic feedback")
}

class M3UHapticFeedback(private val view: View) {
    fun performHapticFeedback(feedbackConstant: Int) {
        view.performHapticFeedback(feedbackConstant)
    }
}

@Composable
fun createM3UHapticFeedback(): M3UHapticFeedback = M3UHapticFeedback(LocalView.current)