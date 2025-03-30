package com.m3u.smartphone.ui.business.favourite

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.m3u.business.favorite.FavoriteViewModel
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.core.util.basic.title
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.isSeries
import com.m3u.data.service.MediaCommand
import com.m3u.core.wrapper.Sort
import com.m3u.i18n.R
import com.m3u.smartphone.ui.material.ktx.interceptVolumeEvent
import com.m3u.core.foundation.ui.thenIf
import com.m3u.smartphone.ui.material.model.LocalHazeState
import com.m3u.smartphone.ui.business.favourite.components.FavoriteGallery
import com.m3u.smartphone.ui.material.components.EpisodesBottomSheet
import com.m3u.smartphone.ui.material.components.MediaSheet
import com.m3u.smartphone.ui.material.components.MediaSheetValue
import com.m3u.smartphone.ui.material.components.SortBottomSheet
import com.m3u.smartphone.ui.common.helper.Action
import com.m3u.smartphone.ui.common.helper.LocalHelper
import com.m3u.smartphone.ui.common.helper.Metadata
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.launch

@Composable
fun FavoriteRoute(
    navigateToChannel: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: FavoriteViewModel = hiltViewModel()
) {
    val title = stringResource(R.string.ui_title_favourite)

    val helper = LocalHelper.current
    val preferences = hiltPreferences()
    val context = LocalContext.current

    val coroutineScope = rememberCoroutineScope()

    val channels = viewModel.channels.collectAsLazyPagingItems()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val zapping by viewModel.zapping.collectAsStateWithLifecycle()
    val sorts = viewModel.sorts
    val sort by viewModel.sort.collectAsStateWithLifecycle()

    val sheetState = rememberModalBottomSheetState()

    var isSortSheetVisible by rememberSaveable { mutableStateOf(false) }
    var mediaSheetValue: MediaSheetValue.FavoriteScreen by remember {
        mutableStateOf(MediaSheetValue.FavoriteScreen())
    }

    val series: Channel? by viewModel.series.collectAsStateWithLifecycle()

    LifecycleResumeEffect(title) {
        Metadata.title = AnnotatedString(title.title())
        Metadata.color = Color.Unspecified
        Metadata.contentColor = Color.Unspecified
        Metadata.actions = listOf(
            Action(
                icon = Icons.AutoMirrored.Rounded.Sort,
                contentDescription = "sort",
                onClick = { isSortSheetVisible = true }
            )
        )
        onPauseOrDispose {
            Metadata.actions = emptyList()
        }
    }

    FavoriteScreen(
        contentPadding = contentPadding,
        rowCount = preferences.rowCount,
        channels = channels,
        zapping = zapping,
        recently = sort == Sort.RECENTLY,
        onClickChannel = { channel ->
            coroutineScope.launch {
                val playlist = viewModel.getPlaylist(channel.playlistUrl)
                when {
                    playlist?.isSeries ?: false -> {
                        viewModel.series.value = channel
                    }

                    else -> {
                        helper.play(MediaCommand.Common(channel.id))
                        navigateToChannel()
                    }
                }
            }
        },
        onLongClickChannel = { mediaSheetValue = MediaSheetValue.FavoriteScreen(it) },
        modifier = Modifier
            .fillMaxSize()
            .thenIf(preferences.godMode) {
                Modifier.interceptVolumeEvent { event ->
                    preferences.rowCount = when (event) {
                        KeyEvent.KEYCODE_VOLUME_UP ->
                            (preferences.rowCount - 1).coerceAtLeast(1)

                        KeyEvent.KEYCODE_VOLUME_DOWN ->
                            (preferences.rowCount + 1).coerceAtMost(2)

                        else -> return@interceptVolumeEvent
                    }
                }
            }
            .then(modifier)
    )

    EpisodesBottomSheet(
        series = series,
        episodes = episodes,
        onEpisodeClick = { episode ->
            coroutineScope.launch {
                series?.let {
                    val input = MediaCommand.XtreamEpisode(
                        channelId = it.id,
                        episode = episode
                    )
                    helper.play(input)
                    navigateToChannel()
                }
            }
        },
        onRefresh = { series?.let { viewModel.seriesReplay.value += 1 } },
        onDismissRequest = { viewModel.series.value = null }
    )
    SortBottomSheet(
        visible = isSortSheetVisible,
        sort = sort,
        sorts = sorts,
        sheetState = sheetState,
        onChanged = { viewModel.sort(it) },
        onDismissRequest = { isSortSheetVisible = false }
    )
    MediaSheet(
        value = mediaSheetValue,
        onFavoriteChannel = { channel ->
            viewModel.favourite(channel.id)
            mediaSheetValue = MediaSheetValue.FavoriteScreen()
        },
        onCreateShortcut = { channel ->
            viewModel.createShortcut(context, channel.id)
            mediaSheetValue = MediaSheetValue.FavoriteScreen()
        },
        onDismissRequest = {
            mediaSheetValue = MediaSheetValue.FavoriteScreen()
            mediaSheetValue = MediaSheetValue.FavoriteScreen()
        }
    )
}

@Composable
private fun FavoriteScreen(
    contentPadding: PaddingValues,
    rowCount: Int,
    channels: LazyPagingItems<Channel>,
    zapping: Channel?,
    recently: Boolean,
    onClickChannel: (Channel) -> Unit,
    onLongClickChannel: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val actualRowCount = when (configuration.orientation) {
        Configuration.ORIENTATION_PORTRAIT -> rowCount
        Configuration.ORIENTATION_LANDSCAPE -> rowCount + 2
        else -> rowCount + 2
    }
    FavoriteGallery(
        contentPadding = contentPadding,
        channels = channels,
        zapping = zapping,
        recently = recently,
        rowCount = actualRowCount,
        onClick = onClickChannel,
        onLongClick = onLongClickChannel,
        modifier = modifier.haze(
            LocalHazeState.current,
            HazeDefaults.style(MaterialTheme.colorScheme.surface)
        )
    )
}