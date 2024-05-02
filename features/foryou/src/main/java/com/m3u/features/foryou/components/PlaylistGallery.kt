package com.m3u.features.foryou.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvGridItemSpan
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.itemsIndexed
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.PlaylistWithCount
import com.m3u.data.database.model.fromLocal
import com.m3u.data.database.model.typeWithSource
import com.m3u.i18n.R.string
import com.m3u.material.ktx.isTelevision
import com.m3u.material.ktx.plus
import com.m3u.material.model.LocalSpacing

@Composable
internal fun PlaylistGallery(
    rowCount: Int,
    playlistCounts: List<PlaylistWithCount>,
    subscribingPlaylistUrls: List<String>,
    onClick: (Playlist) -> Unit,
    onLongClick: (Playlist) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    header: (@Composable () -> Unit)? = null
) {
    val tv = isTelevision()
    if (!tv) {
        SmartphonePlaylistGalleryImpl(
            rowCount = rowCount,
            playlistCounts = playlistCounts,
            subscribingPlaylistUrls = subscribingPlaylistUrls,
            onClick = onClick,
            onLongClick = onLongClick,
            contentPadding = contentPadding,
            modifier = modifier,
            header = header
        )
    } else {
        TvPlaylistGalleryImpl(
            rowCount = rowCount,
            playlistCounts = playlistCounts,
            subscribingPlaylistUrls = subscribingPlaylistUrls,
            onClick = onClick,
            onLongClick = onLongClick,
            contentPadding = contentPadding,
            modifier = modifier,
            header = header
        )
    }
}

@Composable
private fun SmartphonePlaylistGalleryImpl(
    rowCount: Int,
    playlistCounts: List<PlaylistWithCount>,
    subscribingPlaylistUrls: List<String>,
    onClick: (Playlist) -> Unit,
    onLongClick: (Playlist) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null
) {
    val spacing = LocalSpacing.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(rowCount),
        contentPadding = PaddingValues(vertical = spacing.medium) + contentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        modifier = modifier
    ) {
        if (header != null) {
            item(span = { GridItemSpan(rowCount) }) {
                header()
            }
        }
        itemsIndexed(
            items = playlistCounts,
            key = { _, playlistCount -> playlistCount.playlist.url },
            contentType = { _, _ -> }
        ) { index, playlistCount ->
            PlaylistItem(
                label = PlaylistGalleryDefaults.calculateUiTitle(
                    title = playlistCount.playlist.title,
                    fromLocal = playlistCount.playlist.fromLocal
                ),
                type = playlistCount.playlist.typeWithSource,
                count = playlistCount.count,
                subscribing = playlistCount.playlist.url in subscribingPlaylistUrls,
                local = playlistCount.playlist.fromLocal,
                onClick = { onClick(playlistCount.playlist) },
                onLongClick = { onLongClick(playlistCount.playlist) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        PlaylistGalleryDefaults.calculateItemHorizontalPadding(
                            rowCount = rowCount,
                            index = index
                        )
                    )
            )
        }
    }
}

@Composable
private fun TvPlaylistGalleryImpl(
    rowCount: Int,
    playlistCounts: List<PlaylistWithCount>,
    subscribingPlaylistUrls: List<String>,
    onClick: (Playlist) -> Unit,
    onLongClick: (Playlist) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null
) {
    val spacing = LocalSpacing.current
    TvLazyVerticalGrid(
        columns = TvGridCells.Fixed(rowCount),
        contentPadding = PaddingValues(vertical = spacing.medium) + contentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing.large),
        horizontalArrangement = Arrangement.spacedBy(spacing.large),
        modifier = modifier
    ) {
        if (header != null) {
            item(span = { TvGridItemSpan(rowCount) }) {
                header()
            }
        }
        itemsIndexed(
            items = playlistCounts,
            key = { _, it -> it.playlist.url }
        ) { index, playlistCount ->
            PlaylistItem(
                label = PlaylistGalleryDefaults.calculateUiTitle(
                    title = playlistCount.playlist.title,
                    fromLocal = playlistCount.playlist.fromLocal
                ),
                type = playlistCount.playlist.typeWithSource,
                count = playlistCount.count,
                subscribing = playlistCount.playlist.url in subscribingPlaylistUrls,
                local = playlistCount.playlist.fromLocal,
                onClick = { onClick(playlistCount.playlist) },
                onLongClick = { onLongClick(playlistCount.playlist) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        PlaylistGalleryDefaults.calculateItemHorizontalPadding(
                            rowCount = rowCount,
                            index = index,
                            padding = spacing.large
                        )
                    )
            )
        }
    }
}

private object PlaylistGalleryDefaults {
    @Composable
    fun calculateUiTitle(title: String, fromLocal: Boolean): String {
        val actual = title.ifEmpty {
            if (fromLocal) stringResource(string.feat_foryou_imported_playlist_title)
            else ""
        }
        return actual.uppercase()
    }

    @Composable
    fun calculateItemHorizontalPadding(
        rowCount: Int,
        index: Int,
        padding: Dp = LocalSpacing.current.medium
    ): PaddingValues {
        return PaddingValues(
            start = if (index % rowCount == 0) padding else 0.dp,
            end = if (index % rowCount == rowCount - 1) padding else 0.dp
        )
    }
}