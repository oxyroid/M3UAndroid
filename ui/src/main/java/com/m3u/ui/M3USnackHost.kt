package com.m3u.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.m3u.core.wrapper.Message
import com.m3u.material.model.LocalDuration
import com.m3u.material.model.LocalSpacing
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun M3USnackHost(
    message: Message,
    modifier: Modifier = Modifier
) {
    val theme = MaterialTheme.colorScheme
    val spacing = LocalSpacing.current
    val duration = LocalDuration.current

    val television = message.type == Message.TYPE_TELEVISION
    val containerColor by animateColorAsState(
        targetValue = when (message.type) {
            Message.TYPE_TELEVISION -> theme.onBackground
            else -> when (message.level) {
                Message.LEVEL_ERROR -> theme.error
                Message.LEVEL_WARN -> theme.tertiary
                else -> theme.primary
            }
        },
        label = "snack-host-color"
    )
    val contentColor by animateColorAsState(
        targetValue = when (message.type) {
            Message.TYPE_TELEVISION -> theme.background
            else -> when (message.level) {
                Message.LEVEL_ERROR -> theme.onError
                Message.LEVEL_WARN -> theme.onTertiary
                else -> theme.onPrimary
            }
        },
        label = "snack-host-color"
    )
    AnimatedVisibility(
        visible = message.level != Message.LEVEL_EMPTY,
        enter = slideInVertically(
            animationSpec = spring()
        ) { it } + fadeIn(
            animationSpec = spring()
        ),
        exit = slideOutVertically(
            animationSpec = spring()
        ) { it } + fadeOut(
            animationSpec = spring()
        ),
        modifier = Modifier
            .padding(spacing.medium)
            .then(modifier)
    ) {
        val feedback = LocalHapticFeedback.current
        val actualElevation by produceState(spacing.none) {
            delay(duration.fast.milliseconds)
            value = spacing.medium
        }
        val currentElevation by animateDpAsState(
            targetValue = actualElevation,
            label = "snake-host-elevation"
        )
        LaunchedEffect(Unit) {
            delay(duration.fast.milliseconds)
            feedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        Card(
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor
            ),
            elevation = CardDefaults.elevatedCardElevation(currentElevation)
        ) {
            val text = AppSnackHostDefaults.formatText(message)

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (television) {
                    Icon(
                        imageVector = Icons.Rounded.Tv,
                        contentDescription = null
                    )
                }
                Text(
                    text = text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(
                            horizontal = spacing.medium,
                            vertical = spacing.small
                        )
                )
            }

        }
    }
}

object AppSnackHostDefaults {
    @Composable
    fun formatText(message: Message): String {
        return when (message) {
            is Message.Static -> {
                val args = remember(message.formatArgs) {
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

            is Message.Dynamic -> message.value
        }.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.ROOT)
            else it.toString()
        }
    }
}