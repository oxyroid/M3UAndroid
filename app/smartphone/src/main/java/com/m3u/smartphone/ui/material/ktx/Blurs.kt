package com.m3u.smartphone.ui.material.ktx

import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

sealed interface Edge {
    data object Start : Edge
    data object Top : Edge
    data object End : Edge
    data object Bottom : Edge

    val isVertical: Boolean get() = this is Top || this is Bottom
    val isHorizontal: Boolean get() = this is Start || this is End
}

fun Modifier.blurEdge(
    color: Color,
    edge: Edge,
    enable: Boolean = true,
    dimen: Float = BlurDefaults.DIMEN
): Modifier {
    return if (enable) composed {
        val resolvedEdge = resolvePhysicalEdge(edge, LocalLayoutDirection.current)
        Modifier.drawWithCache {
            val brush = brush(
                colors = colors(color, resolvedEdge),
                edge = resolvedEdge,
                full = full(resolvedEdge, size),
                dimen = dimen
            )
            onDrawWithContent {
                drawContent()
                drawRect(
                    brush = brush,
                    topLeft = topLeft(size, resolvedEdge, dimen),
                    size = size(size, resolvedEdge, dimen),
                )
            }
        }
    } else this
}

fun Modifier.blurEdges(
    color: Color,
    edges: List<Edge>,
    enable: Boolean = true,
    dimen: Float = BlurDefaults.DIMEN
): Modifier {
    return if (edges.size == 1) blurEdge(color, edges.first(), enable, dimen)
    else if (!enable || edges.isEmpty()) this else composed {
        val currentColor by animateColorAsState(
            targetValue = color,
            label = "brushColor"
        )
        val layoutDirection = LocalLayoutDirection.current
        Modifier.drawWithCache {
            val brushes = edges.map { logicalEdge ->
                val resolvedEdge = resolvePhysicalEdge(logicalEdge, layoutDirection)
                resolvedEdge to brush(
                    colors = colors(currentColor, resolvedEdge),
                    edge = resolvedEdge,
                    full = full(resolvedEdge, size),
                    dimen = dimen
                )
            }
            onDrawWithContent {
                drawContent()
                brushes.forEach { (edge, brush) ->
                    drawRect(
                        brush = brush,
                        topLeft = topLeft(size, edge, dimen),
                        size = size(size, edge, dimen)
                    )
                }
            }
        }
    }
}

internal fun resolvePhysicalEdge(
    edge: Edge,
    layoutDirection: LayoutDirection,
): Edge = when {
    layoutDirection == LayoutDirection.Rtl && edge == Edge.Start -> Edge.End
    layoutDirection == LayoutDirection.Rtl && edge == Edge.End -> Edge.Start
    else -> edge
}

private fun full(edge: Edge, size: Size): Float = when {
    edge.isVertical -> size.height
    else -> size.width
}

private fun brush(
    colors: List<Color>,
    edge: Edge,
    full: Float,
    dimen: Float
): Brush = when (edge) {
    Edge.Top -> Brush.verticalGradient(colors, startY = 0f, endY = dimen)
    Edge.Bottom -> Brush.verticalGradient(colors, startY = full - dimen, endY = full)
    Edge.Start -> Brush.horizontalGradient(colors, startX = 0f, endX = dimen)
    else -> Brush.horizontalGradient(colors, startX = full - dimen, endX = full)
}

private fun colors(color: Color, edge: Edge): List<Color> {
    val color1 = color.copy(alpha = 0.8f)
    val color2 = color.copy(alpha = 0.3f)
    val color3 = Color.Transparent
    return when (edge) {
        Edge.Start, Edge.Top -> listOf(
            color1,
            color2,
            color3
        )

        Edge.Bottom, Edge.End -> listOf(
            color3,
            color2,
            color1
        )
    }
}

private fun topLeft(
    size: Size,
    edge: Edge,
    dimen: Float
): Offset = when (edge) {
    Edge.Top, Edge.Start -> Offset.Zero
    Edge.Bottom -> Offset(x = 0f, y = size.height - dimen)
    Edge.End -> Offset(x = size.width - dimen, y = 0f)
}

private fun size(
    size: Size,
    edge: Edge,
    dimen: Float
): Size = when {
    edge.isHorizontal -> Size(dimen, size.height)
    else -> Size(size.width, dimen)
}

object BlurDefaults {
    const val DIMEN = 56f
}
