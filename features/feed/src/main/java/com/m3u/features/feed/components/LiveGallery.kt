package com.m3u.features.feed.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyGridState
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import com.m3u.core.architecture.configuration.ExperimentalConfiguration
import com.m3u.core.architecture.configuration.LocalConfiguration
import com.m3u.data.database.entity.Live
import com.m3u.data.database.entity.LiveHolder
import com.m3u.features.feed.NavigateToLive
import com.m3u.features.feed.NavigateToPlaylist
import com.m3u.material.ktx.plus
import com.m3u.material.model.LocalSpacing

@OptIn(ExperimentalConfiguration::class)
@Composable
internal fun LiveGallery(
    state: LazyGridState,
    rowCount: Int,
    liveHolder: LiveHolder,
    navigateToLive: NavigateToLive,
    navigateToPlaylist: NavigateToPlaylist,
    onMenu: (Live) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val spacing = LocalSpacing.current
    val configuration = LocalConfiguration.current

    val lives = liveHolder.lives

    val noPictureMode by configuration.noPictureMode
    val scrollMode by configuration.scrollMode

    LazyVerticalGrid(
        state = state,
        columns = GridCells.Fixed(rowCount),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        contentPadding = PaddingValues(spacing.medium) + contentPadding,
        modifier = modifier.fillMaxSize()
    ) {
        items(
            items = lives,
            key = { live -> live.id },
            contentType = { it.cover.isNullOrEmpty() }
        ) { live ->
            LiveItem(
                live = live,
                noPictureMode = noPictureMode,
                onClick = {
                    if (scrollMode) {
                        val ids = lives.map { it.id }
                        val initialIndex = ids.indexOfFirst { it == live.id }
                        navigateToPlaylist(ids, initialIndex)
                    } else {
                        navigateToLive(live.id)
                    }
                },
                onLongClick = { onMenu(live) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalConfiguration::class)
@Composable
internal fun TvFeedGallery(
    state: TvLazyGridState,
    rowCount: Int,
    liveHolder: LiveHolder,
    navigateToLive: NavigateToLive,
    navigateToPlaylist: NavigateToPlaylist,
    onMenu: (Live) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val spacing = LocalSpacing.current
    val configuration = LocalConfiguration.current
    val lives = liveHolder.lives

    val noPictureMode by configuration.noPictureMode
    val scrollMode by configuration.scrollMode

    TvLazyVerticalGrid(
        state = state,
        columns = TvGridCells.Fixed(rowCount),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        contentPadding = PaddingValues(spacing.medium) + contentPadding,
        modifier = modifier.fillMaxSize()
    ) {
        items(
            items = lives,
            key = { live -> live.id },
            contentType = { it.cover.isNullOrEmpty() }
        ) { live ->
            LiveItem(
                live = live,
                noPictureMode = noPictureMode,
                onClick = {
                    if (scrollMode) {
                        val ids = lives.map { it.id }
                        val initialIndex = ids.indexOfFirst { it == live.id }
                        navigateToPlaylist(ids, initialIndex)
                    } else {
                        navigateToLive(live.id)
                    }
                },
                onLongClick = { onMenu(live) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}