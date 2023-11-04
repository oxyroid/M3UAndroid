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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyGridState
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import com.m3u.data.database.entity.Live
import com.m3u.features.feed.NavigateToLive
import com.m3u.features.feed.NavigateToPlaylist
import com.m3u.material.ktx.plus
import com.m3u.material.model.LocalSpacing

@Composable
internal fun LiveGallery(
    state: LazyGridState,
    rowCount: Int,
    livesFactory: () -> List<Live>,
    noPictureMode: Boolean,
    scrollMode: Boolean,
    navigateToLive: NavigateToLive,
    navigateToPlaylist: NavigateToPlaylist,
    onMenu: (Live) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val spacing = LocalSpacing.current
    val lives = livesFactory()
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

@Composable
internal fun TvFeedGallery(
    state: TvLazyGridState,
    rowCount: Int,
    livesFactory: () -> List<Live>,
    noPictureMode: Boolean,
    scrollMode: Boolean,
    navigateToLive: NavigateToLive,
    navigateToPlaylist: NavigateToPlaylist,
    onMenu: (Live) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val spacing = LocalSpacing.current
    val lives = livesFactory()
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