package com.m3u.smartphone.ui.material.components

import android.graphics.Matrix
import android.view.HapticFeedbackConstants
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.onLongClick as semanticsOnLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m3u.core.foundation.architecture.preferences.ThemeStyle
import com.m3u.core.foundation.components.AbsoluteSmoothCornerShape
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.material.LocalM3UHapticFeedback
import com.m3u.smartphone.ui.material.ktx.InteractionType
import com.m3u.smartphone.ui.material.ktx.Edge
import com.m3u.smartphone.ui.material.ktx.createAppColorScheme
import com.m3u.smartphone.ui.material.ktx.interactionBorder
import com.m3u.smartphone.ui.material.ktx.resolvePhysicalEdge
import com.m3u.smartphone.ui.material.model.LocalSpacing
import java.util.Locale
import kotlin.math.max

/**
 * @param argb: pass -1 means dynamic colors.
 */
@Composable
fun ThemeSelection(
    argb: Int,
    isDark: Boolean,
    themeStyle: Int = ThemeStyle.MATERIAL,
    selected: Boolean,
    themeName: String = argb.toUInt().toString(16).uppercase(Locale.ROOT),
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onLongClickLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current

    val colorScheme = remember(argb, isDark, themeStyle) {
        createAppColorScheme(argb, isDark, themeStyle)
    }
    val feedback = LocalM3UHapticFeedback.current

    val interactionSource = remember { MutableInteractionSource() }
    val activateSelection = {
        if (!selected) {
            feedback.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        }
    }
    val openSelectionEditor = onLongClick?.let { openEditor ->
        {
            feedback.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            openEditor()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(96.dp)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.RadioButton,
                onClick = activateSelection,
                onLongClickLabel = onLongClickLabel,
                onLongClick = openSelectionEditor,
            )
            .clearAndSetSemantics {
                contentDescription = themeName
                this.selected = selected
                role = Role.RadioButton
                semanticsOnClick {
                    activateSelection()
                    true
                }
                if (openSelectionEditor != null) {
                    semanticsOnLongClick(label = onLongClickLabel) {
                        openSelectionEditor()
                        true
                    }
                }
            },
    ) {
        val shape = RoundedCornerShape(24.dp)
        OutlinedCard(
            shape = shape,
            colors = CardDefaults.outlinedCardColors(
                containerColor = colorScheme.surface,
                contentColor = colorScheme.onSurface,
            ),
            border = BorderStroke(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) colorScheme.primary else colorScheme.outlineVariant,
            ),
            modifier = Modifier
                .size(width = 88.dp, height = 76.dp)
                .interactionBorder(
                    type = InteractionType.PRESS,
                    source = interactionSource,
                    shape = shape,
                    color = colorScheme.primary,
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorScheme.background),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 10.dp, bottom = 10.dp)
                        .width(48.dp)
                        .height(18.dp)
                        .clip(CircleShape)
                        .background(colorScheme.primary),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 10.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(colorScheme.secondaryContainer),
                )
                Crossfade(selected, label = "theme-selection-indicator") { isSelected ->
                    Icon(
                        imageVector = if (isSelected) {
                            Icons.Rounded.CheckCircle
                        } else if (isDark) {
                            Icons.Rounded.DarkMode
                        } else {
                            Icons.Rounded.LightMode
                        },
                        contentDescription = null,
                        tint = if (isSelected) {
                            colorScheme.primary
                        } else {
                            colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(24.dp),
                    )
                }
            }
        }
        Text(
            text = themeName,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            modifier = Modifier
                .padding(top = spacing.extraSmall)
                .width(96.dp),
        )
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
    val addDescription = stringResource(string.ui_theme_add)
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
                .padding(spacing.extraSmall)
                .semantics(mergeDescendants = true) {
                    contentDescription = addDescription
                },
            onClick = onClick,
            content = {}
        )

        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            tint = contentColor
        )
    }
}

@Composable
internal fun MessageItem(
    containerColor: Color,
    contentColor: Color,
    alignedToStart: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val logicalTailEdge = if (alignedToStart) Edge.Start else Edge.End
    val tailEdge = resolvePhysicalEdge(logicalTailEdge, LocalLayoutDirection.current)
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = AbsoluteSmoothCornerShape(
            cornerRadiusTL = spacing.small,
            cornerRadiusTR = spacing.small,
            cornerRadiusBL = if (tailEdge == Edge.Start) spacing.none else spacing.small,
            cornerRadiusBR = if (tailEdge == Edge.End) spacing.none else spacing.small
        ),
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .sizeIn(minWidth = if (!alignedToStart) 48.dp else 32.dp)
                .padding(if (alignedToStart) spacing.extraSmall else spacing.small)
        ) {
            Text(
                text = AnnotatedString.fromHtml(contentDescription),
                style = MaterialTheme.typography.bodyLarge
                    .copy(
                        fontSize = if (!alignedToStart) 14.sp
                        else 12.sp
                    ),
                color = contentColor,
                lineHeight = if (!alignedToStart) 16.sp
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
