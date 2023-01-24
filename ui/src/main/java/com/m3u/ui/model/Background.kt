package com.m3u.ui.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

@Immutable
data class Background(
    val color: Color = Color.Unspecified,
    val elevation: Dp = Dp.Unspecified
)

val LocalBackground = staticCompositionLocalOf(::Background)