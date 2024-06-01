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
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.PlaylistWithCount
import com.m3u.data.database.model.epgUrlsOrXtreamXmlUrl
import com.m3u.data.database.model.fromLocal
import com.m3u.data.database.model.type
import com.m3u.i18n.R.string
import com.m3u.material.ktx.plus
import com.m3u.material.model.LocalSpacing

@Composable
internal fun PlaylistGallery(
    rowCount: Int,
    playlistCounts: List<PlaylistWithCount>,
    subscribingPlaylistUrls: List<String>,
    refreshingEpgUrls: List<String>,
    onClick: (Playlist) -> Unit,
    onLongClick: (Playlist) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
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
            key = { _, playlistCount -> playlistCount.playlist.url }
        ) { index, playlistCount ->
            val playlist = playlistCount.playlist
            val count = playlistCount.count
            val subscribing = playlist.url in subscribingPlaylistUrls
            val refreshing = playlist
                .epgUrlsOrXtreamXmlUrl()
                .any { it in refreshingEpgUrls }
            PlaylistItem(
                label = PlaylistGalleryDefaults.calculateUiTitle(
                    title = playlist.title,
                    fromLocal = playlist.fromLocal
                ),
                type = with(playlist) {
                    when (source) {
                        DataSource.M3U -> "$source"
                        DataSource.Xtream -> "$source $type"
                        else -> null
                    }
                },
                count = count,
                subscribingOrRefreshing = subscribing || refreshing,
                local = playlist.fromLocal,
                onClick = { onClick(playlist) },
                onLongClick = { onLongClick(playlist) },
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