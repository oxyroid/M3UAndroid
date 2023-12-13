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
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.data.database.entity.Live
import com.m3u.data.database.entity.LiveHolder
import com.m3u.material.ktx.plus
import com.m3u.material.model.LocalSpacing

typealias PlayStream = (url: String) -> Unit

@Composable
internal fun LiveGallery(
    state: LazyGridState,
    rowCount: Int,
    liveHolder: LiveHolder,
    play: PlayStream,
    onMenu: (Live) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val spacing = LocalSpacing.current
    val pref = LocalPref.current

    val lives = liveHolder.lives
    val floating = liveHolder.floating

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
                border = floating != live,
                noPictureMode = pref.noPictureMode,
                onClick = {
                    play(live.url)
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
    liveHolder: LiveHolder,
    play: PlayStream,
    onMenu: (Live) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val spacing = LocalSpacing.current
    val pref = LocalPref.current
    val lives = liveHolder.lives
    val floating = liveHolder.floating

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
                border = floating != live,
                noPictureMode = pref.noPictureMode,
                onClick = {
                    play(live.url)
                },
                onLongClick = { onMenu(live) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}