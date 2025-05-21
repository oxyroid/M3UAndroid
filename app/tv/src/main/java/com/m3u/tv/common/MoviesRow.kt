package com.m3u.tv.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component1
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component2
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.LazyPagingItems
import androidx.tv.material3.Border
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CompactCard
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.m3u.data.database.model.Channel
import androidx.compose.runtime.ImmutableList
import com.m3u.tv.screens.dashboard.rememberChildPadding
import com.m3u.tv.theme.JetStreamBorderWidth

@Composable
fun ChannelsRow(
    channels: ImmutableList<Channel>,
    modifier: Modifier = Modifier,
    startPadding: Dp = rememberChildPadding().start,
    endPadding: Dp = rememberChildPadding().end,
    title: String? = null,
    titleStyle: TextStyle = MaterialTheme.typography.headlineLarge.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 30.sp
    ),
    onChannelSelected: (channel: Channel) -> Unit = {}
) {
    val (lazyRow, firstItem) = remember { FocusRequester.createRefs() }

    Column(
        modifier = modifier.focusGroup()
    ) {
        if (title != null) {
            Text(
                text = title,
                style = titleStyle,
                modifier = Modifier
                    .alpha(1f)
                    .padding(start = startPadding, top = 16.dp, bottom = 16.dp)
            )
        }
        AnimatedContent(
            targetState = channels,
            label = "",
        ) { channelState ->
            LazyRow(
                contentPadding = PaddingValues(
                    start = startPadding,
                    end = endPadding,
                ),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .focusRequester(lazyRow)
                    .focusRestorer(firstItem)
            ) {
                itemsIndexed(channelState, key = { _, channel -> channel.id }) { index, channel ->
                    val itemModifier = if (index == 0) {
                        Modifier.focusRequester(firstItem)
                    } else {
                        Modifier
                    }
                    ChannelsRowItem(
                        modifier = itemModifier.weight(1f),
                        onChannelSelected = {
                            lazyRow.saveFocusedChild()
                            onChannelSelected(it)
                        },
                        channel = channel
                    )
                }
            }
        }
    }
}

@Composable
fun ChannelsRow(
    channels: LazyPagingItems<Channel>,
    modifier: Modifier = Modifier,
    startPadding: Dp = rememberChildPadding().start,
    endPadding: Dp = rememberChildPadding().end,
    onChannelSelected: (channel: Channel) -> Unit = {}
) {
    LazyRow(
        contentPadding = PaddingValues(
            start = startPadding,
            end = endPadding,
        ),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        modifier = modifier
    ) {
        items(channels.itemCount) { index ->
            val channel = channels[index]
            if (channel != null) {
                ChannelsRowItem(
                    modifier = Modifier.fillParentMaxHeight(),
                    onChannelSelected = onChannelSelected,
                    channel = channel,
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ImmersiveListChannelsRow(
    channels: ImmutableList<Channel>,
    modifier: Modifier = Modifier,
    startPadding: Dp = rememberChildPadding().start,
    endPadding: Dp = rememberChildPadding().end,
    title: String? = null,
    titleStyle: TextStyle = MaterialTheme.typography.headlineLarge.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 30.sp
    ),
    onChannelSelected: (Channel) -> Unit = {},
) {
    val (lazyRow, firstItem) = remember { FocusRequester.createRefs() }

    Column(
        modifier = modifier.focusGroup()
    ) {
        if (title != null) {
            Text(
                text = title,
                style = titleStyle,
                modifier = Modifier
                    .alpha(1f)
                    .padding(start = startPadding)
                    .padding(vertical = 16.dp)
            )
        }
        AnimatedContent(
            targetState = channels,
            label = "",
        ) { channelState ->
            LazyRow(
                contentPadding = PaddingValues(start = startPadding, end = endPadding),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .focusRequester(lazyRow)
                    .focusRestorer(firstItem)
            ) {
                itemsIndexed(
                    channelState,
                    key = { _, channel ->
                        channel.id
                    }
                ) { index, channel ->
                    val itemModifier = if (index == 0) {
                        Modifier.focusRequester(firstItem)
                    } else {
                        Modifier
                    }
                    ChannelsRowItem(
                        modifier = itemModifier.weight(1f),
                        onChannelSelected = {
                            lazyRow.saveFocusedChild()
                            onChannelSelected(it)
                        },
                        channel = channel,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelsRowItem(
    channel: Channel,
    onChannelSelected: (Channel) -> Unit,
    modifier: Modifier = Modifier,
    itemWidth: Dp = 432.dp
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(JetStreamBorderWidth))
        var isFocused by remember { mutableStateOf(false) }
        CompactCard(
            modifier = modifier
                .width(itemWidth)
                .aspectRatio(2f)
                .padding(end = 32.dp)
                .onFocusChanged { isFocused = it.isFocused || it.hasFocus },
            scale = CardDefaults.scale(focusedScale = 1f),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(
                        width = JetStreamBorderWidth, color = MaterialTheme.colorScheme.onSurface
                    )
                )
            ),
            colors = CardDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            onClick = { onChannelSelected(channel) },
            image = {
                val contentAlpha by animateFloatAsState(
                    targetValue = if (isFocused) 1f else 0.5f,
                    label = "",
                )
                AsyncImage(
                    model = channel.cover,
                    contentDescription = channel.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = contentAlpha }
                )
            },
            title = {
                Column {
                    Text(
                        text = channel.category,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Normal
                        ),
                        modifier = Modifier
                            .graphicsLayer { alpha = 0.6f }
                            .padding(start = 24.dp)
                    )
                    Text(
                        text = channel.title,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(
                            start = 24.dp,
                            end = 24.dp,
                            bottom = 24.dp
                        ),
                        // TODO: Remove this when CardContent is not overriding contentColor anymore
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        )
    }
}
