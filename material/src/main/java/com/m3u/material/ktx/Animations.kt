package com.m3u.material.ktx

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.m3u.material.model.LocalDuration

@Composable
fun Color.animated(label: String): State<Color> = animateColorAsState(
    this,
    tween(LocalDuration.current.medium),
    label = label
)

@Composable
inline fun animateColor(label: String, producer: () -> Color): State<Color> = animateColorAsState(
    targetValue = producer(),
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
    targetValue = producer(),
    tween(LocalDuration.current.medium),
    label = label
)

@Composable
inline fun animateInt(label: String, producer: () -> Int): State<Int> = animateIntAsState(
    targetValue = producer(),
    tween(LocalDuration.current.medium),
    label = label
)