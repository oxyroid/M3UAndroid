package com.m3u.smartphone.ui.material.components

import android.graphics.Matrix
import android.view.HapticFeedbackConstants
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m3u.smartphone.ui.material.LocalM3UHapticFeedback
import com.m3u.smartphone.ui.material.ktx.InteractionType
import com.m3u.smartphone.ui.material.ktx.createScheme
import com.m3u.smartphone.ui.material.ktx.interactionBorder
import com.m3u.smartphone.ui.material.model.LocalSpacing
import com.m3u.smartphone.ui.material.model.SugarColors
import com.m3u.smartphone.ui.material.shape.AbsoluteSmoothCornerShape
import kotlin.math.max

/**
 * @param argb: pass -1 means dynamic colors.
 */
@Composable
fun ThemeSelection(
    argb: Int,
    isDark: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current

    val colorScheme = remember(argb, isDark) {
        createScheme(argb, isDark)
    }

    val alpha by animateFloatAsState(
        targetValue = if (selected) 0f else 0.4f,
        label = "alpha"
    )
    val blurRadius by animateFloatAsState(
        targetValue = if (selected) 0f else 16f,
        label = "blur-radius"
    )
    val feedback = LocalM3UHapticFeedback.current

    val interactionSource = remember { MutableInteractionSource() }

    val content: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    if (blurRadius != 0f) renderEffect = BlurEffect(blurRadius, blurRadius)
                }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        color = Color.Black.copy(
                            alpha = alpha
                        )
                    )
                }
        )
    }
    Box(
        contentAlignment = Alignment.Center
    ) {
        val corner by animateDpAsState(
            targetValue = if (!selected) spacing.extraLarge else spacing.medium,
            label = "corner"
        )
        val shape = AbsoluteSmoothCornerShape(corner, 65)
        val brush = Brush.createPremiumBrush(
            colorScheme.primary,
            colorScheme.secondary
        )
        val borderWidth by animateDpAsState(
            if (selected) Dp.Infinity else spacing.extraSmall
        )
        OutlinedCard(
            shape = shape,
            colors = CardDefaults.outlinedCardColors(
                containerColor = colorScheme.background,
                contentColor = colorScheme.onBackground
            ),
            border = BorderStroke(borderWidth, brush),
            modifier = modifier
                .size(64.dp)
                .interactionBorder(
                    type = InteractionType.PRESS,
                    source = interactionSource,
                    shape = shape,
                    color = colorScheme.primary
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    indication = null,
                    interactionSource = null
                )
        ) {
            Box(
                modifier = Modifier.combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = {
                        if (selected) return@combinedClickable
                        feedback.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onClick()
                    },
                    onLongClick = {
                        feedback.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onLongClick()
                    }
                ),
                content = { content() }
            )
        }

        Crossfade(selected, label = "icon") { selected ->
            Icon(
                imageVector = when {
                    selected -> Icons.Rounded.CheckCircle
                    else -> when (isDark) {
                        true -> Icons.Rounded.DarkMode
                        false -> Icons.Rounded.LightMode
                    }
                },
                contentDescription = "icon",
                tint = when {
                    selected -> colorScheme.onPrimary
                    else -> when (isDark) {
                        true -> SugarColors.Tee
                        false -> SugarColors.Yellow
                    }
                }
            )
        }
    }
}

@Composable
fun ThemeAddSelection(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    val spacing = LocalSpacing.current
    Box(
        contentAlignment = Alignment.Center
    ) {
        OutlinedCard(
            shape = RoundedCornerShape(spacing.extraLarge),
            colors = CardDefaults.outlinedCardColors(containerColor, contentColor),
            elevation = CardDefaults.outlinedCardElevation(
                defaultElevation = spacing.none
            ),
            modifier = modifier
                .graphicsLayer {
                    scaleX = 0.8f
                    scaleY = 0.8f
                }
                .size(96.dp)
                .padding(spacing.extraSmall),
            onClick = onClick,
            content = {}
        )

        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = "",
            tint = contentColor
        )
    }
}

@Composable
internal fun MessageItem(
    containerColor: Color,
    contentColor: Color,
    left: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = AbsoluteSmoothCornerShape(
            cornerRadiusTL = spacing.small,
            cornerRadiusTR = spacing.small,
            cornerRadiusBL = if (left) spacing.none else spacing.small,
            cornerRadiusBR = if (!left) spacing.none else spacing.small
        ),
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .sizeIn(minWidth = if (!left) 48.dp else 32.dp)
                .padding(if (left) spacing.extraSmall else spacing.small)
        ) {
            Text(
                text = AnnotatedString.fromHtml(contentDescription),
                style = MaterialTheme.typography.bodyLarge
                    .copy(
                        fontSize = if (!left) 14.sp
                        else 12.sp
                    ),
                color = contentColor,
                lineHeight = if (!left) 16.sp
                else 14.sp
            )
        }
    }
}

fun Modifier.gradientEffect(
    gradientColors: List<Color>,
    animationSpec: InfiniteRepeatableSpec<Float> = infiniteRepeatable(
        animation = tween(
            durationMillis = 2000,
            easing = LinearEasing,
        ),
        repeatMode = RepeatMode.Reverse,
    ),
) = this.composed {
    val infiniteTransition = rememberInfiniteTransition(label = "Gradient")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = animationSpec,
        label = "GradientProgress",
    )

    val gradientBrush = rememberTransformableBrush {
        val colorStops = buildList {
            gradientColors.forEachIndexed { index, color ->
                add((index.toFloat() / gradientColors.size) to color)
            }
        }.toTypedArray()
        Brush.horizontalGradient(
            *colorStops,
            startX = 0f,
            endX = gradientColors.size.toFloat(),
        )
    }

    Modifier.drawWithContent {
        drawContent()
        gradientBrush.transform {
            val x = progress * max(0, gradientColors.size - 2) * size.width
            setScale(size.width, 1f)
            postTranslate(-x, 0f)
        }
        drawRect(brush = gradientBrush)
    }
}

@Composable
inline fun rememberTransformableBrush(
    crossinline getBrush: @DisallowComposableCalls () -> Brush,
): TransformableBrush {
    return remember {
        val brush = getBrush()
        check(brush is ShaderBrush)
        TransformableBrush(brush = brush)
    }
}

@Stable
class TransformableBrush(
    private val brush: ShaderBrush
) : ShaderBrush() {

    override val intrinsicSize: Size
        get() = brush.intrinsicSize

    private var internalShader: Shader? = null
    private val localMatrix: Matrix = Matrix()

    override fun createShader(size: Size): Shader {
        return brush.createShader(size).also {
            internalShader = it
            it.setLocalMatrix(localMatrix)
        }
    }

    // Allows transforming the brush by modifying the localMatrix
    fun transform(transformer: Matrix.() -> Unit) {
        transformer.invoke(localMatrix)
        internalShader?.setLocalMatrix(localMatrix)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TransformableBrush) return false
        if (brush != other.brush) return false
        if (localMatrix != other.localMatrix) return false
        return true
    }

    override fun hashCode(): Int {
        return 31 * brush.hashCode() + localMatrix.hashCode()
    }

    override fun toString(): String {
        return "TransformableBrush(brush=$brush)"
    }
}
