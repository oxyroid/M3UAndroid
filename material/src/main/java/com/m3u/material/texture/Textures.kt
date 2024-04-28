package com.m3u.material.texture

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

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
