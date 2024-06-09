package com.m3u.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    shape: Shape = AbsoluteSmoothCornerShape(LocalSpacing.current.small, 65),
    color: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.contentColorFor(color),
    modifier: Modifier = Modifier
) {
    Badge(
        shape = shape,
        color = color,
        contentColor = contentColor,
        modifier = modifier
    ) {
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