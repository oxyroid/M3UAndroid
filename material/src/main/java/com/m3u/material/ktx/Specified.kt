package com.m3u.material.ktx

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified

inline fun Color.ifUnspecified(block: () -> Color): Color = takeIf { isSpecified } ?: block()
inline fun Dp.ifUnspecified(block: () -> Dp): Dp = takeIf { isSpecified } ?: block()