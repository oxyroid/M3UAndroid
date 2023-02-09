package com.m3u.ui.components

import androidx.compose.material.Badge
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.m3u.ui.model.LocalTheme

@Composable
fun TextBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalTheme.current.tint,
    contentColor: Color = LocalTheme.current.onTint,
    icon: (@Composable () -> Unit)? = null,
) {
    val theme = LocalTheme.current
    Badge(
        modifier = modifier,
        backgroundColor = color,
        contentColor = contentColor
    ) {
        icon?.invoke()
        Text(
            text = text,
            style = MaterialTheme.typography.subtitle2,
            color = theme.onTint
        )
    }
}