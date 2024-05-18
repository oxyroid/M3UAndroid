package com.m3u.features.stream.components

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.paging.compose.LazyPagingItems
import androidx.tv.material3.surfaceColorAtElevation
import com.m3u.core.util.collections.indexOf
import com.m3u.data.database.model.Episode
import com.m3u.data.database.model.Programme
import com.m3u.data.database.model.ProgrammeRange
import com.m3u.data.database.model.Stream
import com.m3u.data.service.MediaCommand
import com.m3u.material.components.Background
import com.m3u.material.ktx.isTelevision
import com.m3u.material.ktx.thenIf
import com.m3u.material.model.LocalSpacing
import com.m3u.material.shape.AbsoluteSmoothCornerShape
import com.m3u.ui.FontFamilies
import com.m3u.ui.helper.LocalHelper
import kotlinx.coroutines.launch
import androidx.tv.material3.Card as TvCard
import androidx.tv.material3.CardDefaults as TvCardDefaults
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

@Composable
internal fun PlayerPanel(
    title: String,
    playlistTitle: String,
    streamId: Int,
    isChannelsSupported: Boolean,
    isProgrammeSupported: Boolean,
    isPanelExpanded: Boolean,
    channels: LazyPagingItems<Stream>,
    programmes: LazyPagingItems<Programme>,
    programmeRange: ProgrammeRange,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val spacing = LocalSpacing.current

    val useVertical = configuration.screenWidthDp < configuration.screenHeightDp
    Background(
        shape = if (useVertical) RectangleShape else AbsoluteSmoothCornerShape(
            cornerRadiusTL = spacing.medium,
            cornerRadiusBL = spacing.medium
        )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.small),
            modifier = modifier
                .fillMaxSize()
                .thenIf(!useVertical) {
                    Modifier.statusBarsPadding()
                }
                .padding(vertical = spacing.medium)
        ) {
            AnimatedVisibility(
                visible = isPanelExpanded && useVertical,
                modifier = Modifier.padding(horizontal = spacing.medium)
            ) {
                Column {
                    Text(
                        text = title.trim(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee()
                    )
                    Text(
                        text = playlistTitle.trim().uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        color = LocalContentColor.current.copy(0.54f),
                        fontFamily = FontFamilies.LexendExa,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee()
                    )
                }
            }

            if (isChannelsSupported) {
                ChannelGallery(
                    // TODO
                    value = ChannelGalleryValue.PagingChannel(channels, streamId),
                    isPanelExpanded = isPanelExpanded,
                    vertical = !isProgrammeSupported
                )
            }

            if (isProgrammeSupported) {
                ProgramGuide(
                    isPanelExpanded = isPanelExpanded,
                    programmes = programmes,
                    range = programmeRange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ChannelGallery(
    value: ChannelGalleryValue,
    isPanelExpanded: Boolean,
    modifier: Modifier = Modifier,
    vertical: Boolean = false
) {
    val spacing = LocalSpacing.current
    val lazyListState = rememberLazyListState()
    val tv = isTelevision()

    ScrollToCurrentEffect(
        value = value,
        isPanelExpanded = isPanelExpanded,
        lazyListState = lazyListState
    )

    val content: LazyListScope.() -> Unit = {
        when (value) {
            is ChannelGalleryValue.PagingChannel -> {
                val channels = value.channels
                val streamId = value.streamId
                items(channels.itemCount) { i ->
                    channels[i]?.let { stream ->
                        val isPlaying = stream.id == streamId
                        ChannelGalleryItem(
                            stream = stream,
                            isPlaying = isPlaying
                        )
                    }
                }
            }

            is ChannelGalleryValue.XtreamEpisode -> {
                items(value.episodes) { series ->
                    // TODO
                }
            }
        }
    }
    if (!vertical) {
        LazyRow(
            state = lazyListState,
            horizontalArrangement = Arrangement.spacedBy(spacing.medium),
            contentPadding = PaddingValues(spacing.medium),
            modifier = modifier,
            content = content
        )
    } else {
        val focusManager = LocalFocusManager.current
        LazyColumn(
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
            contentPadding = PaddingValues(spacing.medium),
            modifier = modifier
                .fillMaxWidth()
                .thenIf(tv) {
                    Modifier.onKeyEvent {
                        when (it.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                focusManager.moveFocus(FocusDirection.Exit)
                                true
                            }

                            else -> false
                        }
                    }
                },
            content = content
        )
    }
}

private sealed class ChannelGalleryValue {
    data class PagingChannel(
        val channels: LazyPagingItems<Stream>,
        val streamId: Int
    ) : ChannelGalleryValue()

    data class XtreamEpisode(
        val episodes: List<Episode>,
        val seriesId: Int
    ) : ChannelGalleryValue()
}

@Composable
private fun ChannelGalleryItem(
    stream: Stream,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val helper = LocalHelper.current
    val coroutineScope = rememberCoroutineScope()
    val tv = isTelevision()

    if (!tv) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (!isPlaying)
                    MaterialTheme.colorScheme.surfaceColorAtElevation(spacing.medium)
                else MaterialTheme.colorScheme.onSurface,
                contentColor = if (!isPlaying) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.surfaceColorAtElevation(spacing.small)
            ),
            shape = AbsoluteRoundedCornerShape(spacing.medium),
            elevation = CardDefaults.cardElevation(spacing.none),
            onClick = {
                if (isPlaying) return@Card
                coroutineScope.launch {
                    helper.play(
                        MediaCommand.Common(stream.id)
                    )
                }
            },
            modifier = modifier
        ) {
            Text(
                text = stream.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold.takeIf { isPlaying },
                modifier = Modifier.padding(spacing.medium)
            )
        }
    } else {
        TvCard(
            colors = TvCardDefaults.colors(
                containerColor = if (!isPlaying)
                    TvMaterialTheme.colorScheme.surfaceColorAtElevation(spacing.medium)
                else TvMaterialTheme.colorScheme.onSurface,
                contentColor = if (!isPlaying) TvMaterialTheme.colorScheme.onSurface
                else TvMaterialTheme.colorScheme.surfaceColorAtElevation(spacing.small)
            ),
            onClick = {
                if (isPlaying) return@TvCard
                coroutineScope.launch {
                    helper.play(
                        MediaCommand.Common(stream.id)
                    )
                }
            },
            modifier = modifier
        ) {
            TvText(
                text = stream.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold.takeIf { isPlaying },
                modifier = Modifier.padding(spacing.medium)
            )
        }
    }
}

@Composable
private fun ScrollToCurrentEffect(
    value: ChannelGalleryValue,
    isPanelExpanded: Boolean,
    lazyListState: LazyListState,
    scrollOffset: Int = -120
) {
    if (isPanelExpanded) {
        when (value) {
            is ChannelGalleryValue.PagingChannel -> {
                val channels = value.channels
                val streamId = value.streamId
                LaunchedEffect(channels.itemCount) {
                    var index = -1
                    for (i in 0 until channels.itemCount) {
                        if (channels[i]?.id == streamId) {
                            index = i
                            break
                        }
                    }
                    if (index != -1) {
                        lazyListState.animateScrollToItem(index, scrollOffset)
                    }
                }
            }

            is ChannelGalleryValue.XtreamEpisode -> {
                val episodes = value.episodes
                val seriesId = value.seriesId
                LaunchedEffect(episodes.size) {
                    val index = episodes.indexOf { it.id == seriesId }
                    if (index != -1) {
                        lazyListState.animateScrollToItem(index, scrollOffset)
                    }
                }
            }
        }
    }
}
