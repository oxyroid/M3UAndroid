package com.m3u.material.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class StepColor(
    val color: Color,
    val step: Int
)