package com.m3u.features.stream.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
//import androidx.compose.animation.ExperimentalSharedTransitionApi
//import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.paging.compose.LazyPagingItems
import com.m3u.data.database.model.Stream
import com.m3u.data.service.MediaCommand
import com.m3u.material.components.Background
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.FontFamilies
import com.m3u.ui.helper.LocalHelper
import kotlinx.coroutines.launch

//@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun
//        SharedTransitionScope.
        StreamPanel(
    title: String,
    playlistTitle: String,
    streamId: Int,
    isSeriesPlaylist: Boolean,
    isPanelExpanded: Boolean,
    neighboring: LazyPagingItems<Stream>,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    Background {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(vertical = spacing.medium)
        ) {
            AnimatedVisibility(
                visible = isPanelExpanded,
                modifier = Modifier.padding(horizontal = spacing.medium)
            ) {
                Text(
                    text = title.trim(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .basicMarquee()
//                        .sharedElement(
//                            state = rememberSharedContentState("stream-title"),
//                            this
//                        )
                )
            }
            AnimatedVisibility(
                visible = isPanelExpanded,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.padding(horizontal = spacing.medium)
            ) {
                Text(
                    text = playlistTitle.trim().uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    color = LocalContentColor.current.copy(0.54f),
                    fontFamily = FontFamilies.LexendExa,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .basicMarquee()
//                        .sharedElement(
//                            state = rememberSharedContentState("playlist-title"),
//                            this
//                        )
                )
            }

            if (!isSeriesPlaylist) {
                StreamPanelNeighboringSelector(
                    neighboring = neighboring,
                    streamId = streamId,
                    isPanelExpanded = isPanelExpanded
                )
            }
        }
    }
}

@Composable
// TODO: Support Xtream Series Episodes.
private fun StreamPanelNeighboringSelector(
    neighboring: LazyPagingItems<Stream>,
    streamId: Int,
    isPanelExpanded: Boolean,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val helper = LocalHelper.current

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    AnimateScrollToStreamEffect(
        neighboring = neighboring,
        streamId = streamId,
        isPanelExpanded = isPanelExpanded,
        lazyListState = lazyListState
    )

    LazyRow(
        state = lazyListState,
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        contentPadding = PaddingValues(spacing.medium),
        modifier = modifier
    ) {
        items(neighboring.itemCount) { i ->
            neighboring[i]?.let { currentStream ->
                val playing = currentStream.id == streamId
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (!playing) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.onSurface,
                        contentColor = if (!playing) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.surface
                    ),
                    shape = AbsoluteRoundedCornerShape(spacing.medium),
                    elevation = CardDefaults.cardElevation(
                        if (playing) spacing.none else spacing.small
                    ),
                    onClick = {
                        coroutineScope.launch {
                            helper.play(
                                MediaCommand.Live(currentStream.id)
                            )
                        }
                    }
                ) {
                    Text(
                        text = currentStream.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold.takeIf { playing },
                        modifier = Modifier.padding(spacing.medium)
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimateScrollToStreamEffect(
    neighboring: LazyPagingItems<Stream>,
    streamId: Int,
    isPanelExpanded: Boolean,
    lazyListState: LazyListState,
    // FIXME: Don't use hard number.
    scrollOffset: Int = -120
) {
    if (isPanelExpanded) {
        LaunchedEffect(neighboring.itemCount) {
            var index = -1
            for (i in 0 until neighboring.itemCount) {
                if (neighboring[i]?.id == streamId) {
                    index = i
                    break
                }
            }
            if (index != -1) {
                lazyListState.animateScrollToItem(index, scrollOffset)
            }
        }
    }
}