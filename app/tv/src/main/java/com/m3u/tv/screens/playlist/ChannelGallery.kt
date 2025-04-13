package com.m3u.tv.screens.playlist

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component1
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.Border
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CompactCard
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.m3u.business.playlist.PlaylistViewModel
import com.m3u.core.foundation.components.AbsoluteSmoothCornerShape
import com.m3u.core.foundation.ui.thenIf
import com.m3u.data.database.model.Channel
import com.m3u.tv.theme.JetStreamBorderWidth
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

fun LazyListScope.channelGallery(
    channels: List<PlaylistViewModel.CategoryWithChannels>,
    pinnedCategories: List<String>,
    onPinOrUnpinCategory: (String) -> Unit,
    onHideCategory: (String) -> Unit,
    startPadding: Dp,
    endPadding: Dp,
    onChannelClick: (channel: Channel) -> Unit,
    reloadThumbnail: suspend (channelUrl: String) -> Uri?,
    syncThumbnail: suspend (channelUrl: String) -> Uri?,
) {
    itemsIndexed(channels, key = { _, (category, _) -> category }) { i, (category, channels) ->
        var hasFocus by remember { mutableStateOf(false) }
        Column {
            val pagingChannels = channels.collectAsLazyPagingItems()
            AnimatedVisibility(
                visible = hasFocus,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = category,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .padding(
                            start = startPadding,
                            end = endPadding,
                            top = 16.dp,
                            bottom = 24.dp
                        )
                )
            }
            val showControl: Boolean by produceState(initialValue = false, hasFocus) {
                if (hasFocus) {
                    delay(1600.milliseconds)
                    value = true
                } else {
                    value = false
                }
            }
            LazyRow(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { hasFocus = it.hasFocus }
                    .heightIn(min = 160.dp)
                    .focusRestorer()
                    .thenIf(!hasFocus) {
                        Modifier.drawWithContent {
                            drawContent()
                            drawRect(Color.Black.copy(0.4f))
                        }
                    },
                contentPadding = PaddingValues(
                    start = startPadding,
                    end = endPadding,
                    bottom = 16.dp
                ),
            ) {
                val (controlRef) = FocusRequester.createRefs()
                item {
                    AnimatedVisibility(
                        visible = showControl,
                        enter = fadeIn(animationSpec = tween(delayMillis = 400)) + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally(animationSpec = tween(delayMillis = 400)),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(
                                8.dp,
                                Alignment.CenterVertically
                            ),
                            modifier = Modifier
                                .focusGroup()
                                .focusRequester(controlRef)
                                .fillMaxHeight()
                                .padding(end = startPadding)
                        ) {
                            val pinned = category in pinnedCategories
                            IconButton(
                                colors = IconButtonDefaults.colors(
                                    containerColor = if (pinned) MaterialTheme.colorScheme.primary.copy(
                                        alpha = 0.8f
                                    )
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                    focusedContainerColor = if (pinned) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                ),
                                onClick = {
                                    onPinOrUnpinCategory(category)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PushPin,
                                    contentDescription = "PushPin",
                                )
                            }
                            IconButton(
                                onClick = {
                                    onHideCategory(category)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VisibilityOff,
                                    contentDescription = "VisibilityOff"
                                )
                            }
                        }
                    }
                }
                items(pagingChannels.itemCount, key = { j -> j }) { j ->
                    val channel = pagingChannels[j]
                    if (channel != null) {
                        ChannelGalleryItem(
                            itemWidth = 382.dp,
                            onChannelClick = onChannelClick,
                            reloadThumbnail = reloadThumbnail,
                            syncThumbnail = syncThumbnail,
                            channel = channel,
                            modifier = Modifier
                                .thenIf(j == 0 && showControl) {
                                    Modifier.focusProperties {
                                        start = controlRef
                                    }
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelGalleryItem(
    itemWidth: Dp,
    channel: Channel,
    modifier: Modifier = Modifier,
    onChannelClick: (channel: Channel) -> Unit,
    reloadThumbnail: suspend (channelUrl: String) -> Uri?,
    syncThumbnail: suspend (channelUrl: String) -> Uri?,
) {
    val context = LocalContext.current
    val crossfadeImageRequest = { data: Any? ->
        ImageRequest.Builder(context)
            .crossfade(800)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .data(data)
            .build()
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(JetStreamBorderWidth))
        var isFocused by remember { mutableStateOf(false) }
        val shape = AbsoluteSmoothCornerShape(16.dp, 100)
        CompactCard(
            modifier = modifier
                .width(itemWidth)
                .aspectRatio(2f)
                .padding(end = 32.dp)
                .onFocusChanged { isFocused = it.isFocused || it.hasFocus },
            scale = CardDefaults.scale(focusedScale = 1.1f),
            shape = CardDefaults.shape(shape),
            border = CardDefaults.border(
                border = Border(
                    BorderStroke(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.border
                    ),
                    shape = shape
                ),
                focusedBorder = Border(
                    BorderStroke(width = 4.dp, color = Color.White),
                    shape = shape
                ),
                pressedBorder = Border(
                    BorderStroke(
                        width = 4.dp,
                        color = MaterialTheme.colorScheme.border
                    ),
                    shape = shape
                )
            ),
            onClick = { onChannelClick(channel) },
            image = {
                val contentAlpha by animateFloatAsState(
                    targetValue = if (isFocused) 1f else 0.5f,
                    label = "",
                )
                val model: Any? by produceState<Any?>(
                    initialValue = channel.cover,
                    key1 = isFocused
                ) {
                    val default = channel.cover
                    if (isFocused) {
                        val channelUrl = channel.url
                        delay(600.milliseconds)
                        val reloaded = reloadThumbnail(channelUrl)
                            ?.let { crossfadeImageRequest(it) }
                        if (reloaded == null) {
                            value = syncThumbnail(channelUrl)
                                ?.let { crossfadeImageRequest(it) }
                                ?: default
                        } else {
                            value = reloaded
                            delay(600.milliseconds)
                            value = syncThumbnail(channelUrl)
                                ?.let { crossfadeImageRequest(it) } ?: default
                        }
                    } else {
                        value = default
                    }
                }
                AsyncImage(
                    model = model,
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
