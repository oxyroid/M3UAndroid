package com.m3u.features.favorite.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.data.database.entity.Stream
import com.m3u.material.ktx.plus
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.LocalHelper
import kotlinx.collections.immutable.ImmutableList

// TODO: replace with material3-carousel.
@Composable
internal fun FavouriteGallery(
    contentPadding: PaddingValues,
    streams: ImmutableList<Stream>,
    zapping: Stream? = null,
    rowCount: Int,
    navigateToStream: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val pref = LocalPref.current
    val helper = LocalHelper.current
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(rowCount),
        verticalItemSpacing = spacing.medium,
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
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
                noPictureMode = pref.noPictureMode,
                border = zapping != stream,
                onClick = {
                    helper.play(stream.url)
                    navigateToStream()
                },
                onLongClick = {},
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}