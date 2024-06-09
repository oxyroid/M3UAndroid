package com.m3u.feature.foryou.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
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
import com.m3u.material.model.LocalHazeState
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.helper.LocalHelper
import com.m3u.ui.helper.Metadata
import com.m3u.ui.helper.useRailNav
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.math.absoluteValue

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
    val configuration = LocalConfiguration.current
    val helper = LocalHelper.current

    val colorScheme = MaterialTheme.colorScheme

    val headlineAspectRatio = Metadata.headlineAspectRatio(helper.useRailNav)

    val state = rememberLazyGridState()
    val viewportStartOffset by remember {
        derivedStateOf {
            if (state.firstVisibleItemIndex == 0) state.firstVisibleItemScrollOffset
            else -Int.MAX_VALUE
        }
    }
    val currentHazeColor by animateColorAsState(
        targetValue = lerp(
            start = Color.Transparent,
            stop = colorScheme.surface,
            fraction = Metadata.headlineFraction
        ),
        label = "playlist-gallery-haze-color"
    )
    LaunchedEffect(configuration.screenWidthDp) {
        snapshotFlow { viewportStartOffset }
            .onEach {
                val fraction = (it.absoluteValue /
                        (configuration.screenWidthDp * headlineAspectRatio))
                    .coerceIn(0f, 1f)
                Metadata.headlineFraction = fraction
            }
            .launchIn(this)
    }
    LazyVerticalGrid(
        state = state,
        columns = GridCells.Fixed(rowCount),
        contentPadding = PaddingValues(vertical = spacing.medium) + contentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        modifier = modifier
            .haze(
                LocalHazeState.current,
                HazeDefaults.style(currentHazeColor)
            )
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