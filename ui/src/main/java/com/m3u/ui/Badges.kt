package com.m3u.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.m3u.material.model.LocalSpacing
import com.m3u.material.shape.AbsoluteSmoothCornerShape

@Composable
fun Badge(
    modifier: Modifier = Modifier,
    shape: Shape = AbsoluteSmoothCornerShape(LocalSpacing.current.small, 65),
    color: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.contentColorFor(color),
    block: @Composable () -> Unit
) {
    val spacing = LocalSpacing.current
    val currentColor by animateColorAsState(
        targetValue = color,
        label = "color"
    )
    val currentContentColor by animateColorAsState(
        targetValue = contentColor,
        label = "content-color"
    )
    Box(
        modifier = Modifier
            .clip(shape)
            .background(currentColor)
            .padding(
                start = spacing.small,
                end = spacing.small,
                bottom = 1.dp
            )
            .then(modifier),
        contentAlignment = Alignment.Center
    ) {
        val textStyle = MaterialTheme.typography.bodyMedium.copy(
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None,
            )
        )
        CompositionLocalProvider(
            LocalTextStyle provides textStyle,
            LocalContentColor provides currentContentColor
        ) {
            block()
        }
    }
}

@Composable
fun TextBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    Badge(modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None
                )
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            textAlign = TextAlign.Center,
            fontFamily = FontFamilies.LexendExa
        )
    }
}

@Suppress("unused")
object BadgeDefaults {
    @Composable
    fun shape(
        corner: Dp = LocalSpacing.current.small,
        rect: Dp = LocalSpacing.current.extraSmall,
        orientation: Orientation = Orientation.Horizontal,
        reverse: Boolean = true,
        count: Int,
        index: Int
    ): Shape {
        check(index in 0..<count) { "index should in [0, count)" }
        val layoutDirection = LocalLayoutDirection.current
        return when (layoutDirection) {
            LayoutDirection.Ltr -> {
                AbsoluteSmoothCornerShape(
//            cornerRadiusTL = spacing.extraSmall,
//            cornerRadiusTR = spacing.extraSmall,
//            cornerRadiusBL = spacing.extraSmall,
//            cornerRadiusBR = spacing.small
                )
            }

            LayoutDirection.Rtl -> {
                AbsoluteSmoothCornerShape(
//            cornerRadiusTL = spacing.extraSmall,
//            cornerRadiusTR = spacing.extraSmall,
//            cornerRadiusBL = spacing.extraSmall,
//            cornerRadiusBR = spacing.small
                )
            }
        }
    }
}

enum class UnstableValue {
    EXPERIMENTAL, ALPHA, BETA
}

@Composable
fun UnstableBadge(
    value: UnstableValue,
    modifier: Modifier = Modifier
) {
    TextBadge(
        text = when (value) {
            UnstableValue.EXPERIMENTAL -> "EXPERIMENTAL"
            UnstableValue.ALPHA -> "ALPHA"
            UnstableValue.BETA -> "BETA"
        },
        modifier = modifier
    )
}