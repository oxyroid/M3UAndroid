package com.m3u.ui.util

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.m3u.ui.model.LocalDuration

@Composable
fun Color.animated(label: String): State<Color> = animateColorAsState(
    this,
    tween(LocalDuration.current.medium),
    label = label
)

@Composable
inline fun animateColor(label: String, producer: () -> Color): State<Color> = animateColorAsState(
    targetValue = producer.invoke(),
    tween(LocalDuration.current.medium),
    label = label
)

@Composable
fun Dp.animated(label: String): State<Dp> = animateDpAsState(
    this,
    tween(LocalDuration.current.medium),
    label = label
)

@Composable
inline fun animateDp(label: String, producer: () -> Dp): State<Dp> = animateDpAsState(
    targetValue = producer.invoke(),
    tween(LocalDuration.current.medium),
    label = label
)
