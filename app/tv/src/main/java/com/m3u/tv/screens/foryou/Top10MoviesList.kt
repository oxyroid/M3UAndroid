package com.m3u.tv.screens.foryou

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.m3u.data.database.model.Channel
import com.m3u.tv.common.ImmersiveListChannelsRow
import androidx.compose.runtime.ImmutableList
import com.m3u.tv.common.PosterImage
import com.m3u.tv.screens.dashboard.rememberChildPadding
import com.m3u.tv.utils.bringIntoViewIfChildrenAreFocused

@Composable
fun Top10ChannelsList(
    channels: ImmutableList<Channel>,
    modifier: Modifier = Modifier,
    gradientColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
    onChannelClick: (channel: Channel) -> Unit
) {
    var isListFocused by remember { mutableStateOf(false) }
    var selectedChannel by remember(channels) { mutableStateOf(channels.first()) }

    val sectionTitle = if (isListFocused) {
        null
    } else {
        "stringResource(R.string.top_10_channels_title)"
    }

    ImmersiveList(
        selectedChannel = selectedChannel,
        isListFocused = isListFocused,
        gradientColor = gradientColor,
        channels = channels,
        sectionTitle = sectionTitle,
        onChannelClick = onChannelClick,
        onChannelFocused = {
            selectedChannel = it
        },
        onFocusChanged = {
            isListFocused = it.hasFocus
        },
        modifier = modifier.bringIntoViewIfChildrenAreFocused(
            PaddingValues(bottom = 116.dp)
        )
    )
}

@Composable
private fun ImmersiveList(
    selectedChannel: Channel,
    isListFocused: Boolean,
    gradientColor: Color,
    channels: ImmutableList<Channel>,
    sectionTitle: String?,
    onFocusChanged: (FocusState) -> Unit,
    onChannelFocused: (Channel) -> Unit,
    onChannelClick: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.BottomStart,
        modifier = modifier
    ) {
        Background(
            channel = selectedChannel,
            visible = isListFocused,
            modifier = modifier
                .height(432.dp)
                .gradientOverlay(gradientColor)
        )
        Column {
            if (isListFocused) {
                ChannelDescription(
                    channel = selectedChannel,
                    modifier = Modifier.padding(
                        start = rememberChildPadding().start,
                        bottom = 40.dp
                    )
                )
            }

            ImmersiveListChannelsRow(
                channels = channels,
                title = sectionTitle,
                onChannelSelected = onChannelClick,
                modifier = Modifier.onFocusChanged(onFocusChanged)
            )
        }
    }
}

@Composable
private fun Background(
    channel: Channel,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Crossfade(
            targetState = channel,
            label = "posterUriCrossfade",

            ) {
            PosterImage(channel = it, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ChannelDescription(
    channel: Channel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = channel.title, style = MaterialTheme.typography.displaySmall)
        Text(
            modifier = Modifier.fillMaxWidth(0.5f),
            text = channel.category,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            fontWeight = FontWeight.Light
        )
    }
}

private fun Modifier.gradientOverlay(gradientColor: Color): Modifier =
    drawWithCache {
        val horizontalGradient = Brush.horizontalGradient(
            colors = listOf(
                gradientColor,
                Color.Transparent
            ),
            startX = size.width.times(0.2f),
            endX = size.width.times(0.7f)
        )
        val verticalGradient = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                gradientColor
            ),
            endY = size.width.times(0.3f)
        )
        val linearGradient = Brush.linearGradient(
            colors = listOf(
                gradientColor,
                Color.Transparent
            ),
            start = Offset(
                size.width.times(0.2f),
                size.height.times(0.5f)
            ),
            end = Offset(
                size.width.times(0.9f),
                0f
            )
        )

        onDrawWithContent {
            drawContent()
            drawRect(horizontalGradient)
            drawRect(verticalGradient)
            drawRect(linearGradient)
        }
    }
