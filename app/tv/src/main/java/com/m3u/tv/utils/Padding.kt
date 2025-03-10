package com.m3u.tv.utils

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp

@Immutable
data class Padding(
    val start: Dp,
    val top: Dp,
    val end: Dp,
    val bottom: Dp,
)
