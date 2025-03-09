package com.m3u.business.favorite.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.m3u.core.wrapper.Resource
import com.m3u.data.database.model.Channel
import com.m3u.material.components.VerticalDraggableScrollbar
import com.m3u.material.ktx.plus
import com.m3u.material.model.LocalSpacing

@Composable
internal fun FavouriteGallery(
    contentPadding: PaddingValues,
    channels: Resource<List<Channel>>,
    zapping: Channel?,
    recently: Boolean,
    rowCount: Int,
    onClick: (Channel) -> Unit,
    onLongClick: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    Box(modifier) {
        when (channels) {
            Resource.Loading -> {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(contentPadding)
                )
            }

            is Resource.Success -> {
                Row(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(start = spacing.medium),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    @Suppress("NAME_SHADOWING")
                    val channels = channels.data
                    val lazyStaggeredGridState = rememberLazyStaggeredGridState()

                    LazyVerticalStaggeredGrid(
                        state = lazyStaggeredGridState,
                        columns = StaggeredGridCells.Fixed(rowCount),
                        verticalItemSpacing = spacing.medium,
                        horizontalArrangement = Arrangement.spacedBy(spacing.large),
                        contentPadding = PaddingValues(vertical = spacing.medium) + contentPadding,
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f),
                    ) {
//                    item(span = StaggeredGridItemSpan.FullLine) {
//                        RandomTips(
//                            onClick = onClickRandomTips
//                        )
//                    }
                        items(
                            items = channels,
                            key = { it.id },
                            contentType = { it.cover.isNullOrEmpty() }
                        ) { channel ->
                            FavoriteItem(
                                channel = channel,
                                zapping = zapping == channel,
                                onClick = { onClick(channel) },
                                onLongClick = { onLongClick(channel) },
                                recently = recently,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    VerticalDraggableScrollbar(lazyStaggeredGridState = lazyStaggeredGridState)
                }
            }

            is Resource.Failure -> {}
        }
    }
}
