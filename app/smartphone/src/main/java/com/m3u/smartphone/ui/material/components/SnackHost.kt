package com.m3u.smartphone.ui.material.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.m3u.core.foundation.wrapper.Message
import com.m3u.data.service.collectMessageAsState
import com.m3u.smartphone.ui.material.model.LocalDuration
import com.m3u.smartphone.ui.material.model.LocalSpacing
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun SnackHost(
    modifier: Modifier = Modifier
) {
    val theme = MaterialTheme.colorScheme
    val spacing = LocalSpacing.current
    val duration = LocalDuration.current
    val feedback = LocalHapticFeedback.current
    val accessibilityManager = LocalAccessibilityManager.current
    val coroutineScope = rememberCoroutineScope()

    val message by collectMessageAsState()
    var displayedMessage by remember { mutableStateOf<Message>(Message.Dynamic.EMPTY) }
    var dismissJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(message, accessibilityManager) {
        if (message.level == Message.LEVEL_EMPTY) return@LaunchedEffect

        displayedMessage = message
        dismissJob?.cancel()
        val originalTimeoutMillis = message.duration.inWholeMilliseconds.coerceAtLeast(0L)
        val recommendedTimeoutMillis = calculateSnackTimeoutMillis(
            originalTimeoutMillis = originalTimeoutMillis,
            recommendedTimeoutMillis = accessibilityManager?.calculateRecommendedTimeoutMillis(
                originalTimeoutMillis = originalTimeoutMillis,
                containsIcons = message.type == Message.TYPE_TELEVISION,
                containsText = true,
                containsControls = false,
            ),
        )
        if (recommendedTimeoutMillis != Long.MAX_VALUE) {
            val emittedMessage = message
            dismissJob = coroutineScope.launch {
                delay(recommendedTimeoutMillis.milliseconds)
                if (displayedMessage == emittedMessage) {
                    displayedMessage = Message.Dynamic.EMPTY
                }
            }
        }
    }

    val tv by remember {
        derivedStateOf { displayedMessage.type == Message.TYPE_TELEVISION }
    }

    val currentContainerColor by animateColorAsState(
        targetValue = when (displayedMessage.type) {
            Message.TYPE_TELEVISION -> theme.onBackground
            else -> when (displayedMessage.level) {
                Message.LEVEL_ERROR -> theme.error
                Message.LEVEL_WARN -> theme.tertiary
                else -> theme.primary
            }
        },
        label = "snack-host-color"
    )
    val currentContentColor by animateColorAsState(
        targetValue = when (displayedMessage.type) {
            Message.TYPE_TELEVISION -> theme.background
            else -> when (displayedMessage.level) {
                Message.LEVEL_ERROR -> theme.onError
                Message.LEVEL_WARN -> theme.onTertiary
                else -> theme.onPrimary
            }
        },
        label = "snack-host-color"
    )
    AnimatedVisibility(
        visible = displayedMessage.level != Message.LEVEL_EMPTY,
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
        modifier = modifier
    ) {
        LaunchedEffect(displayedMessage) {
            if (displayedMessage.level != Message.LEVEL_EMPTY) {
                delay(duration.fast.milliseconds)
                feedback.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
        Card(
            colors = CardDefaults.cardColors(
                containerColor = currentContainerColor,
                contentColor = currentContentColor
            ),
            elevation = CardDefaults.elevatedCardElevation(0.dp),
            modifier = Modifier
                .animateContentSize()
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                }
        ) {
            val text = displayedMessage.formatText()

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
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.alignByBaseline()
                )
            }
        }
    }
}

internal fun calculateSnackTimeoutMillis(
    originalTimeoutMillis: Long,
    recommendedTimeoutMillis: Long?,
): Long {
    return recommendedTimeoutMillis
        ?.coerceAtLeast(originalTimeoutMillis)
        ?: originalTimeoutMillis
}
