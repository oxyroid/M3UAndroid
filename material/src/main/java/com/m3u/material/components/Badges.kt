package com.m3u.material.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.m3u.material.model.LocalSpacing
import com.m3u.material.model.LocalTheme

@Composable
fun TextBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalTheme.current.tint,
    contentColor: Color = LocalTheme.current.onTint,
    icon: (@Composable () -> Unit)? = null,
) {
    val theme = LocalTheme.current
    val spacing = LocalSpacing.current

    Surface(
        modifier = modifier,
        color = color,
        contentColor = contentColor,
        shape = RoundedCornerShape(spacing.small)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.invoke()
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.subtitle2,
                fontSize = MaterialTheme.typography.subtitle2.fontSize,
                color = theme.onTint,
                modifier = Modifier.padding(
                    horizontal = spacing.extraSmall
                )
            )
        }

    }
}
