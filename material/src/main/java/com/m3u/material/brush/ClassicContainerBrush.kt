package com.m3u.material.brush

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.sqrt

fun RecommendCardContainerBrush(size: Size): Brush = Brush.radialGradient(
    colors = listOf(
        Color(red = 28, green = 27, blue = 31, alpha = 0),
        Color(red = 28, green = 27, blue = 31, alpha = 162),
        Color(red = 28, green = 27, blue = 31, alpha = 204),
        Color(red = 28, green = 27, blue = 31, alpha = 224),
    ),
    center = Offset(
        x = size.width / 3 * 2,
        y = size.height / 3
    ),
    radius = (size.minDimension * sqrt(2.0)).toFloat()
)
fun ImmersiveBackgroundBrush(size: Size): Brush = Brush.radialGradient(
    colors = listOf(
        Color(red = 28, green = 27, blue = 31, alpha = 0),
        Color(red = 28, green = 27, blue = 31, alpha = 204),
        Color(red = 28, green = 27, blue = 31, alpha = 255),
        Color(red = 28, green = 27, blue = 31, alpha = 255),
    ),
    center = Offset(
        x = size.width / 2,
        y = size.height / 3
    ),
    radius = (size.minDimension * sqrt(2.0)).toFloat()
)