package com.m3u.ui.util

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color
import com.m3u.ui.model.LocalDuration

@Composable
fun Color.animated(): State<Color> = animateColorAsState(
    this,
    tween(LocalDuration.current.medium)
)