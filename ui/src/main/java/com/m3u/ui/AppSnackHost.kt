package com.m3u.ui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.m3u.core.wrapper.Message
import com.m3u.material.model.LocalSpacing
import java.util.Locale

@Composable
fun AppSnackHost(
    message: Message,
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
        visible = message.level != Message.LEVEL_EMPTY,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        val feedback = LocalHapticFeedback.current
        LaunchedEffect(Unit) {
            feedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        Card(
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor
            )
        ) {
            val text = when {
                message.level == Message.LEVEL_EMPTY -> return@Card
                message is Message.Static -> {
                    val args = remember(message) {
                        message.formatArgs.flatMap {
                            when (it) {
                                is Array<*> -> it.toList().filterNotNull()
                                is Collection<*> -> it.toList().filterNotNull()
                                else -> listOf(it)
                            }
                        }.toTypedArray()
                    }
                    stringResource(message.resId, *args)
                }

                message is Message.Dynamic -> message.value
                else -> return@Card
            }.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.ROOT)
                else it.toString()
            }
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(
                        horizontal = spacing.medium,
                        vertical = spacing.small
                    )
            )
        }
    }
}