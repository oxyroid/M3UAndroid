package com.m3u.favorite.components

import androidx.compose.foundation.layout.Arrangement
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
import com.m3u.ui.local.LocalSpacing
import com.m3u.ui.local.LocalTheme

@Composable
internal fun BrightIcon(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalTheme.current.tint,
    contentColor: Color = LocalTheme.current.onTint,
    icon: (@Composable () -> Unit)? = null,
) {
    Surface(
        color = color,
        contentColor = contentColor,
        shape = RoundedCornerShape(LocalSpacing.current.extraSmall)
    ) {
        Row(
            modifier = modifier
                .padding(
                    horizontal = LocalSpacing.current.extraSmall
                ),
            horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.extraSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                icon()
            }
            Text(
                text = text,
                style = MaterialTheme.typography.subtitle2,
                color = LocalTheme.current.onTint
            )
        }
    }
}