package com.m3u.material.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun CircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = CircularProgressIndicatorDefaults.color,
    size: Dp = 16.dp
) {
    val count = 8
    val currentCount by produceState(0) {
        while (true) {
            value += 1
            delay(60.milliseconds)
        }
    }
    Canvas(
        modifier = modifier.size(size)
    ) {
        val r = this.size.width / 2
        val drawWidth = 0.50 * r
        val strokeWidth = 0.32 * r
        val rotateAngle = (360 / count).toDouble()
        repeat(count) { index ->
            val i = index + 1
            val startX =
                (r + (r - drawWidth) * cos(Math.toRadians(rotateAngle * i))).toFloat()
            val startY =
                (r - (r - drawWidth) * sin(Math.toRadians(rotateAngle * i))).toFloat()
            val endX = (r + r * cos(Math.toRadians(rotateAngle * i))).toFloat()
            val endY = (r - r * sin(Math.toRadians(rotateAngle * i))).toFloat()
            val alpha = ((i + currentCount) / count.toFloat()) % 1f
            drawLine(
                color = color.copy(alpha = alpha),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                cap = StrokeCap.Round,
                strokeWidth = strokeWidth.toFloat(),
            )
        }
    }
}

object CircularProgressIndicatorDefaults {
    val color = Color.Gray
}
