package com.m3u.androidApp.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.m3u.material.model.LocalSpacing
import com.m3u.material.model.LocalTheme

@Composable
fun AppSnackHost(
    message: String,
    modifier: Modifier = Modifier
) {
    val theme = LocalTheme.current
    val spacing = LocalSpacing.current
    val containerColor = theme.tint
    val contentColor = theme.onTint
    AnimatedVisibility(
        visible = message.isNotEmpty(),
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor
            )
        ) {
            Text(
                text = message,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.subtitle1,
                modifier = Modifier
                    .padding(
                        horizontal = spacing.medium,
                        vertical = spacing.small
                    )
            )
        }
    }
}