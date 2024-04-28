package com.m3u.material.texture

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.texture(
    texture: Texture
): Modifier {
    return drawBehind {
        when (texture) {
            is Texture.Mesh -> drawMesh(
                texture.color,
                texture.width,
                texture.xSpacing,
                texture.ySpacing,
                texture.xAdditional,
                texture.yAdditional
            )
        }
    }
}

sealed class Texture {
    data class Mesh(
        val color: Color,
        val width: Float,
        val xSpacing: Float,
        val ySpacing: Float = xSpacing,
        val xAdditional: Float = 0f,
        val yAdditional: Float = xAdditional,
    ) : Texture()
}

private fun DrawScope.drawMesh(
    color: Color,
    width: Float,
    xSpacing: Float,
    ySpacing: Float,
    xAdditional: Float,
    yAdditional: Float,
) {
    var xTotal = size.width
    var yTotal = size.height
    while (xTotal >= xSpacing) {
        xTotal -= xSpacing
        drawLine(
            color = color,
            start = Offset(xTotal + xAdditional, 0f),
            end = Offset(xTotal + xAdditional, size.height),
            strokeWidth = width
        )
    }
    while (yTotal >= ySpacing) {
        yTotal -= ySpacing
        drawLine(
            color = color,
            start = Offset(0f, yTotal + yAdditional),
            end = Offset(size.width, yTotal + yAdditional),
            strokeWidth = width
        )
    }
}

@Preview
@Composable
private fun TexturesPreview() {
    val color1 = MaterialTheme.colorScheme.surfaceColorAtElevation(0.dp)
    val color2 = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)
    val transition = rememberInfiniteTransition()
    val color by transition.animateColor(
        initialValue = color1,
        targetValue = color2,
        animationSpec = infiniteRepeatable(
            animation = tween(5000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "textures-01"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(100.dp)
            .background(
                Brush.radialGradient(
                    listOf(
                        color1,
                        color2
                    )
                )
            )
            .texture(
                texture = Texture.Mesh(
                    color = color,
                    width = 8f,
                    xSpacing = 72f,
                    xAdditional = 24f
                ),
            )
    )
}