package com.m3u.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m3u.core.wrapper.Message
import com.m3u.data.service.collectMessageAsState
import com.m3u.material.components.Icon
import com.m3u.material.model.LocalDuration
import com.m3u.material.model.LocalSpacing
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun SnackHost(
    modifier: Modifier = Modifier
) {
    val theme = MaterialTheme.colorScheme
    val spacing = LocalSpacing.current
    val duration = LocalDuration.current
    val feedback = LocalHapticFeedback.current

    val message by collectMessageAsState()

    val tv by remember {
        derivedStateOf { message.type == Message.TYPE_TELEVISION }
    }

    val interactionSource = remember { MutableInteractionSource() }

    val isPressed by interactionSource.collectIsPressedAsState()

    val currentContainerColor by animateColorAsState(
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
    val currentContentColor by animateColorAsState(
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
    val currentScale by animateFloatAsState(
        targetValue = if (isPressed) 1.02f else 1f,
        label = "snack-host-scale"
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
            .graphicsLayer {
                scaleX = currentScale
                scaleY = currentScale
            }
            .then(modifier)
    ) {
        LaunchedEffect(Unit) {
            delay(duration.fast.milliseconds)
            feedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        Card(
            colors = CardDefaults.cardColors(
                containerColor = currentContainerColor,
                contentColor = currentContentColor
            ),
            elevation = CardDefaults.elevatedCardElevation(0.dp),
            onClick = { },
            interactionSource = interactionSource,
            modifier = Modifier.animateContentSize()
        ) {
            val text = message.formatText()

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                modifier = Modifier.padding(
                    horizontal = spacing.medium,
                    vertical = spacing.small
                )
            ) {
                when {
                    tv -> {
                        Icon(
                            imageVector = Icons.Rounded.Tv,
                            contentDescription = null
                        )
                    }
                }
                Crossfade(
                    targetState = isPressed,
                    label = "snake-host-text"
                ) { isPressed ->
                    Text(
                        text = text,
                        maxLines = if (isPressed) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.alignByBaseline()
                    )
                }
            }
        }
    }
}
