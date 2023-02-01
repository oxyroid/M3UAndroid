package com.m3u.subscription.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalTheme

@Composable
internal fun SchemeIcon(
    scheme: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = LocalTheme.current.tint,
        contentColor = LocalTheme.current.onTint,
        shape = RoundedCornerShape(LocalSpacing.current.extraSmall)
    ) {
        Box(
            modifier = modifier
                .padding(
                    horizontal = LocalSpacing.current.extraSmall
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = scheme,
                style = MaterialTheme.typography.subtitle2,
                color = LocalTheme.current.onTint
            )
        }
    }
}