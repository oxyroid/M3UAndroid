package com.m3u.ui.firework

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.util.lerp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

interface FireworkState {
    val incoming: SharedFlow<Unit>
    suspend fun emit()
}

@Composable
fun rememberFireworkState(): FireworkState = remember {
    object : FireworkState {
        override val incoming = MutableSharedFlow<Unit>()

        override suspend fun emit() {
            incoming.emit(Unit)
        }
    }
}

data class FireworkElement(
    val degree: Float,
    val radius: Float,
    val alpha: Float,
    val offset: Offset,
    val color: Color
)

@Composable
fun Modifier.firework(
    state: FireworkState,
    durationMillis: Int = 1000
): Modifier {
    var animatables by remember {
        mutableStateOf<List<Animatable<Float, AnimationVector1D>>>(emptyList())
    }
    val fractions by remember {
        derivedStateOf { animatables.map { it.value } }
    }

    val elements by remember {
        derivedStateOf {
            fractions.map { fraction ->
                FireworkElement(
                    degree = lerp(0f, 180f, fraction),
                    radius = lerp(16f, 48f, fraction),
                    alpha = lerp(0f, 1f, fraction),
                    offset = lerp(
                        start = Offset(0f, 0f),
                        stop = Offset(240f, -240f),
                        fraction = fraction
                    ),
                    color = lerp(
                        start = Color.Gray,
                        stop = Color(0xffffcd3c),
                        fraction = fraction
                    )
                )
            }
        }
    }
    LaunchedEffect(state.incoming) {
        snapshotFlow { fractions }
            .onEach {
                Log.e("TAG", "fractions: $it")
            }
            .launchIn(this)
        state.incoming.collect {
            animatables += Animatable(0f).apply {
                animateTo(1f, tween(durationMillis, easing = LinearEasing))
            }
        }
    }
    return drawWithContent {
        drawContent()
        elements.forEach { (degree, radius, alpha, offset, color) ->
            translate(left = offset.x, top = offset.y) {
                rotate(degree) {
                    drawRect(
                        color = Color(0xff466e9a),
                        style = Stroke(4f)
                    )
                    drawPath(
                        RoundedPolygon.star(
                            numVerticesPerRadius = 5,
                            radius = radius,
                            innerRadius = radius / 2,
                            rounding = CornerRounding(8f),
                            centerX = size.width / 2
                        )
                            .toPath()
                            .asComposePath()
                            .apply {
                                translate(
                                    Offset(x = 0f, y = size.height / 2f)
                                )
                            },
                        color = color,
                        alpha = alpha
                    )
                }
            }
        }
    }
}

@Composable
@Preview
fun FireworkPreview() {
    val coroutineScope = rememberCoroutineScope()
    val fireworkState = rememberFireworkState()
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Button(
            onClick = {
                coroutineScope.launch {
                    fireworkState.emit()
                }
            },
            modifier = Modifier.firework(fireworkState, 1800)
        ) {
            Text("Click Me")
        }
    }
}