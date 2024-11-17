package com.m3u.material.overscroll

import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import com.google.android.material.math.MathUtils.lerp

internal const val DefaultHeaderHeight = 800f

internal class OverscrollGraphicsLayerModifier(
    private val overScrollState: OverScrollState,
    private val headerHeight: Float = DefaultHeaderHeight,
    private val maxOffset: Float = overScrollState.maxOffset,
    private val maxScaleMultiple: Float = 1.0f,
    private val maxParallaxOffset: Float = 100f,
    private val finalAlpha: Float = 1.0f,
    private val rotationMultiple: Float = 0f,
    private val effect: OverScrollEffect = OverScrollEffect.Scale
) : LayoutModifier {

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val offset = overScrollState.offSet
        val process = offset / maxOffset

        var newTranslationY = 0f
        var newTranslationX = 0f
        var newAlpha = 1.0f
        var newRotationZ = 0f
        var newRotationX = 0f
        var newRotationY = 0f

        val newHeaderHeight = headerHeight + offset
        val placeable = if (effect == OverScrollEffect.Header) {
            measurable.measure(
                constraints.copy(
                    minHeight = newHeaderHeight.toInt(),
                    maxHeight = newHeaderHeight.toInt()
                )
            )
        } else {
            measurable.measure(constraints)
        }

        val scaleFactor = lerp(1.0f, maxScaleMultiple, process)
        val newHeight = scaleFactor * placeable.measuredHeight

        when (effect) {
            OverScrollEffect.Scale -> {
                newTranslationX = (placeable.width * (scaleFactor - 1)) / 2
                newTranslationY = (placeable.height * (scaleFactor - 1)) / 2
            }

            OverScrollEffect.ScaleCenter -> {
            }

            OverScrollEffect.ParallaxVertical -> {
                newTranslationY = lerp(0f, maxParallaxOffset, process)
            }

            OverScrollEffect.ParallaxHorizontal -> {
                newTranslationX = lerp(0f, maxParallaxOffset, process)
            }

            OverScrollEffect.Header -> {
            }

            OverScrollEffect.Alpha -> {
                newAlpha = lerp(1.0f, finalAlpha, process)
            }

            OverScrollEffect.RotationCenter -> {
                newRotationZ = lerp(0f, rotationMultiple * 360, process)
            }

            OverScrollEffect.RotationHorizontal -> {
                newRotationY = lerp(0f, rotationMultiple * 360, process)
            }

            OverScrollEffect.RotationVertical -> {
                newRotationX = lerp(0f, rotationMultiple * 360, process)
            }
        }
        val layerBlock: GraphicsLayerScope.() -> Unit = {
            scaleX = scaleFactor
            scaleY = scaleFactor
            translationY = newTranslationY
            translationX = newTranslationX
            alpha = newAlpha
            rotationZ = newRotationZ
            rotationX = newRotationX
            rotationY = newRotationY
        }
        return layout(placeable.width, newHeight.toInt()) {
            placeable.placeWithLayer(0, 0, layerBlock = layerBlock)
        }
    }
}