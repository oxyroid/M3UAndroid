package com.m3u.smartphone.ui.business.favourite.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.m3u.core.foundation.components.CircularProgressIndicator
import com.m3u.data.database.model.Channel
import com.m3u.smartphone.ui.material.components.VerticalDraggableScrollbar
import com.m3u.smartphone.ui.material.ktx.plus
import com.m3u.smartphone.ui.material.model.LocalSpacing

@Composable
internal fun FavoriteGallery(
    contentPadding: PaddingValues,
    channels: LazyPagingItems<Channel>,
    zapping: Channel?,
    recently: Boolean,
    rowCount: Int,
    onClick: (Channel) -> Unit,
    onLongClick: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(start = spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        @Suppress("NAME_SHADOWING")
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
                count = channels.itemCount,
            ) {
                val channel = channels[it]
                if (channel == null) {
                    CircularProgressIndicator()
                } else {
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
        }
        VerticalDraggableScrollbar(lazyStaggeredGridState = lazyStaggeredGridState)
    }
}
