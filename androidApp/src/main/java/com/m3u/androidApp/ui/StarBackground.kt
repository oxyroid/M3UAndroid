package com.m3u.androidApp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.material.ktx.tv

data class StarSpec(
    val numVertices: Int,
    /** Size in relative to the available width. */
    val size: Float,
    /** 0f: Center, 1f: Outside to the right, 1f: Outside to the left. */
    val offset: Offset,
    val color: Color,
    val blurRadius: Dp,
)

class StarColors(
    val firstColor: Color,
    val secondColor: Color,
    val thirdColor: Color,
) {
    companion object {
        @Composable
        fun defaults() = StarColors(
            firstColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            secondColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            thirdColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
        )
    }
}

private fun createStarSpecs(colors: StarColors) = listOf(
    StarSpec(
        numVertices = 10,
        size = 1.2f,
        offset = Offset(-0.3f, -0.18f),
        color = colors.secondColor,
        blurRadius = 24.dp,
    ),
    StarSpec(
        numVertices = 12,
        size = 0.5f,
        offset = Offset(0.45f, -0.45f),
        color = colors.firstColor,
        blurRadius = 32.dp,
    ),
    StarSpec(
        numVertices = 6,
        size = 0.7f,
        offset = Offset(0.3f, 0.2f),
        color = colors.thirdColor,
        blurRadius = 40.dp,
    )
)

@Composable
fun StarBackground(
    modifier: Modifier = Modifier,
    colors: StarColors = StarColors.defaults(),
) {
    val tv = tv()
    val preferences = hiltPreferences()
    val specs = remember(colors) { createStarSpecs(colors) }
    AnimatedVisibility(
        visible = !tv && preferences.colorfulBackground,
        enter = fadeIn() + scaleIn(initialScale = 2.3f),
        exit = fadeOut() + scaleOut(targetScale = 2.3f),
        modifier = modifier
    ) {
        val infiniteTransition = rememberInfiniteTransition()
        val degree by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(8000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotate-degree"
        )
        Box(
            Modifier
                .fillMaxSize()
                .blur(24.dp)
                .drawBehind {
                    val width = size.width
                    val height = size.height
                    val minDimension = size.minDimension
                    for (spec in specs) {
                        val star = RoundedPolygon.star(
                            numVerticesPerRadius = spec.numVertices,
                            radius = minDimension * spec.size / 2,
                            innerRadius = minDimension * spec.size * 0.7f / 2,
                            rounding = CornerRounding(minDimension * 0.05f),
                            centerX = width / 2,
                            centerY = height / 2
                        )
                        val path = star
                            .toPath()
                            .asComposePath()

                        translate(
                            width * spec.offset.x,
                            height * spec.offset.y
                        ) {
                            rotate(180f * degree * (spec.numVertices - 6).coerceAtLeast(1)) {
                                drawPath(path, color = spec.color)
                            }
                        }
                    }
                }
        )
    }
}
