package com.m3u.features.foryou.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.m3u.features.foryou.model.PlaylistDetail
import com.m3u.i18n.R.string
import com.m3u.material.ktx.plus
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.UiMode
import com.m3u.ui.currentUiMode
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun PlaylistGallery(
    rowCount: Int,
    details: ImmutableList<PlaylistDetail>,
    navigateToPlaylist: (Playlist) -> Unit,
    onMenu: (Playlist) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    header: (@Composable () -> Unit)? = null
) {
    when (currentUiMode()) {
        UiMode.Default -> {
            PlaylistGalleryImpl(
                rowCount = rowCount,
                details = details,
                navigateToPlaylist = navigateToPlaylist,
                onMenu = onMenu,
                contentPadding = contentPadding,
                modifier = modifier,
                header = header
            )
        }

        UiMode.Television -> {
            TvPlaylistGalleryImpl(
                rowCount = rowCount,
                details = details,
                navigateToPlaylist = navigateToPlaylist,
                onMenu = onMenu,
                contentPadding = contentPadding,
                modifier = modifier,
                header = header
            )
        }

        UiMode.Compat -> {
            CompactPlaylistGalleryImpl(
                rowCount = rowCount,
                details = details,
                navigateToPlaylist = navigateToPlaylist,
                onMenu = onMenu,
                contentPadding = contentPadding,
                modifier = modifier,
                header = header
            )
        }
    }
}

@Composable
private fun PlaylistGalleryImpl(
    rowCount: Int,
    details: ImmutableList<PlaylistDetail>,
    navigateToPlaylist: (Playlist) -> Unit,
    onMenu: (Playlist) -> Unit,
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
            items = details,
            key = { _, detail -> detail.playlist.url },
            contentType = { _, _ -> }
        ) { index, detail ->
            PlaylistItem(
                label = PlaylistGalleryDefaults.calculateUiTitle(
                    title = detail.playlist.title,
                    fromLocal = detail.playlist.fromLocal
                ),
                type = detail.playlist.type,
                typeWithSource = detail.playlist.typeWithSource,
                number = detail.count,
                local = detail.playlist.fromLocal,
                onClick = { navigateToPlaylist(detail.playlist) },
                onLongClick = { onMenu(detail.playlist) },
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
    details: ImmutableList<PlaylistDetail>,
    navigateToPlaylist: (Playlist) -> Unit,
    onMenu: (Playlist) -> Unit,
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
            items = details,
            key = { _, it -> it.playlist.url },
            contentType = { _, _ -> }
        ) { index, detail ->
            PlaylistItem(
                label = PlaylistGalleryDefaults.calculateUiTitle(
                    title = detail.playlist.title,
                    fromLocal = detail.playlist.fromLocal
                ),
                type = detail.playlist.type,
                typeWithSource = detail.playlist.typeWithSource,
                number = detail.count,
                local = detail.playlist.fromLocal,
                onClick = { navigateToPlaylist(detail.playlist) },
                onLongClick = { onMenu(detail.playlist) },
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

@Composable
private fun CompactPlaylistGalleryImpl(
    rowCount: Int,
    details: ImmutableList<PlaylistDetail>,
    navigateToPlaylist: (Playlist) -> Unit,
    onMenu: (Playlist) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(rowCount),
        contentPadding = contentPadding,
        modifier = modifier
    ) {
        if (header != null) {
            item(span = { GridItemSpan(rowCount) }) {
                header()
            }
        }
        items(
            items = details,
            key = { it.playlist.url },
            contentType = {}
        ) { detail ->
            PlaylistItem(
                label = PlaylistGalleryDefaults.calculateUiTitle(
                    title = detail.playlist.title,
                    fromLocal = detail.playlist.fromLocal
                ),
                type = detail.playlist.type,
                typeWithSource = detail.playlist.typeWithSource,
                number = detail.count,
                local = detail.playlist.fromLocal,
                onClick = { navigateToPlaylist(detail.playlist) },
                onLongClick = { onMenu(detail.playlist) },
                modifier = Modifier.fillMaxWidth()
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