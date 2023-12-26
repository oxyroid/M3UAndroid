package com.m3u.androidApp.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.m3u.core.wrapper.Message
import com.m3u.material.model.LocalSpacing

@Composable
fun AppSnackHost(
    message: Message.Dynamic,
    modifier: Modifier = Modifier
) {
    val theme = MaterialTheme.colorScheme
    val spacing = LocalSpacing.current
    val containerColor by animateColorAsState(
        targetValue = when (message.level) {
            Message.LEVEL_ERROR -> theme.error
            Message.LEVEL_WARN -> theme.tertiary
            else -> theme.primary
        },
        label = "snack-host-color"
    )
    val contentColor by animateColorAsState(
        targetValue = when (message.level) {
            Message.LEVEL_ERROR -> theme.onError
            Message.LEVEL_WARN -> theme.onTertiary
            else -> theme.onPrimary
        },
        label = "snack-host-color"
    )
    AnimatedVisibility(
        visible = message != Message.Dynamic.EMPTY,
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
                text = message.value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .padding(
                        horizontal = spacing.medium,
                        vertical = spacing.small
                    )
            )
        }
    }
}