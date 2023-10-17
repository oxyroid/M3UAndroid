package com.m3u.material.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class GradientColors(
    val top: Color = Color.Unspecified,
    val container: Color = Color.Unspecified,
    val bottom: Color = Color.Unspecified
)

val LocalGradientColors = staticCompositionLocalOf(::GradientColors)
