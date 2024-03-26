package com.m3u.features.favorite.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import com.m3u.core.wrapper.Resource
import com.m3u.data.database.model.Stream
import com.m3u.material.ktx.isTelevision
import com.m3u.material.ktx.plus
import com.m3u.material.model.LocalSpacing
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun FavouriteGallery(
    contentPadding: PaddingValues,
    streamsResource: Resource<ImmutableList<Stream>>,
    zapping: Stream?,
    recently: Boolean,
    rowCount: Int,
    onClick: (Stream) -> Unit,
    onLongClick: (Stream) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        when (streamsResource) {
            Resource.Loading -> {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(contentPadding)
                )
            }

            is Resource.Success -> {
                FavouriteGalleryImpl(
                    contentPadding = contentPadding,
                    streams = streamsResource.data,
                    zapping = zapping,
                    recently = recently,
                    rowCount = rowCount,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
            }

            is Resource.Failure -> {}
        }
    }
}

@Composable
private fun FavouriteGalleryImpl(
    contentPadding: PaddingValues,
    streams: ImmutableList<Stream>,
    zapping: Stream?,
    recently: Boolean,
    rowCount: Int,
    onClick: (Stream) -> Unit,
    onLongClick: (Stream) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val tv = isTelevision()
    if (!tv) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(rowCount),
            verticalItemSpacing = spacing.medium,
            horizontalArrangement = Arrangement.spacedBy(spacing.large),
            contentPadding = PaddingValues(spacing.medium) + contentPadding,
            modifier = modifier.fillMaxSize(),
        ) {
            items(
                items = streams,
                key = { it.id },
                contentType = { it.cover.isNullOrEmpty() }
            ) { stream ->
                FavoriteItem(
                    stream = stream,
                    zapping = zapping == stream,
                    onClick = { onClick(stream) },
                    onLongClick = { onLongClick(stream) },
                    recently = recently,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    } else {
        TvLazyVerticalGrid(
            columns = TvGridCells.Fixed(rowCount),
            verticalArrangement = Arrangement.spacedBy(spacing.large),
            horizontalArrangement = Arrangement.spacedBy(spacing.large),
            contentPadding = PaddingValues(
                vertical = spacing.medium,
                horizontal = spacing.large
            ) + contentPadding,
            modifier = modifier.fillMaxSize(),
        ) {
            items(
                items = streams,
                key = { it.id },
                contentType = { it.cover.isNullOrEmpty() }
            ) { stream ->
                FavoriteItem(
                    stream = stream,
                    zapping = zapping == stream,
                    recently = recently,
                    onClick = { onClick(stream) },
                    onLongClick = { onLongClick(stream) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
