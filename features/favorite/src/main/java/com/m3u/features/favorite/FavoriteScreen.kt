package com.m3u.features.favorite

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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.architecture.preferences.LocalPreferences
import com.m3u.core.util.basic.title
import com.m3u.core.wrapper.Resource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.Stream
import com.m3u.data.service.MediaCommand
import com.m3u.features.favorite.components.FavouriteGallery
import com.m3u.i18n.R
import com.m3u.material.components.Background
import com.m3u.material.ktx.interceptVolumeEvent
import com.m3u.material.ktx.isTelevision
import com.m3u.material.ktx.thenIf
import com.m3u.material.model.LocalHazeState
import com.m3u.ui.Destination
import com.m3u.ui.EpisodesBottomSheet
import com.m3u.ui.LocalVisiblePageInfos
import com.m3u.ui.MediaSheet
import com.m3u.ui.MediaSheetValue
import com.m3u.ui.Sort
import com.m3u.ui.SortBottomSheet
import com.m3u.ui.TvSortFullScreenDialog
import com.m3u.ui.helper.Action
import com.m3u.ui.helper.LocalHelper
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.launch

@Composable
fun FavouriteRoute(
    navigateToStream: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: FavouriteViewModel = hiltViewModel()
) {
    val tv = isTelevision()

    val title = stringResource(R.string.ui_title_favourite)

    val helper = LocalHelper.current
    val preferences = LocalPreferences.current
    val context = LocalContext.current

    val coroutineScope = rememberCoroutineScope()

    val streamsResource by viewModel.streamsResource.collectAsStateWithLifecycle()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val zapping by viewModel.zapping.collectAsStateWithLifecycle()
    val sorts = viewModel.sorts
    val sort by viewModel.sort.collectAsStateWithLifecycle()

    val sheetState = rememberModalBottomSheetState()

    var isSortSheetVisible by rememberSaveable { mutableStateOf(false) }
    var mediaSheetValue: MediaSheetValue.FavouriteScreen by remember {
        mutableStateOf(MediaSheetValue.FavouriteScreen())
    }

    val visiblePageInfos = LocalVisiblePageInfos.current
    val pageIndex = remember { Destination.Root.entries.indexOf(Destination.Root.Favourite) }
    val isPageInfoVisible = remember(pageIndex, visiblePageInfos) {
        visiblePageInfos.find { it.index == pageIndex } != null
    }

    val series: Stream? by viewModel.series.collectAsStateWithLifecycle()

    if (isPageInfoVisible) {
        LifecycleResumeEffect(title) {
            helper.title = title.title()
            helper.actions = listOf(
                Action(
                    icon = Icons.AutoMirrored.Rounded.Sort,
                    contentDescription = "sort",
                    onClick = { isSortSheetVisible = true }
                )
            )
            onPauseOrDispose {
                helper.actions = emptyList()
            }
        }
    }

    Background {
        FavoriteScreen(
            contentPadding = contentPadding,
            rowCount = preferences.rowCount,
            streamsResource = streamsResource,
            zapping = zapping,
            recently = sort == Sort.RECENTLY,
            onClickStream = { stream ->
                coroutineScope.launch {
                    val playlist = viewModel.getPlaylist(stream.playlistUrl)
                    when {
                        playlist?.type in Playlist.SERIES_TYPES -> {
                            viewModel.series.value = stream
                        }

                        else -> {
                            helper.play(MediaCommand.Live(stream.id))
                            navigateToStream()
                        }
                    }
                }
            },
            onLongClickStream = { mediaSheetValue = MediaSheetValue.FavouriteScreen(it) },
            onClickRandomTips = {
                viewModel.playRandomly()
                navigateToStream()
            },
            modifier = Modifier
                .fillMaxSize()
                .thenIf(!tv && preferences.godMode) {
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
                            streamId = it.id,
                            episode = episode
                        )
                        helper.play(input)
                        navigateToStream()
                    }
                }
            },
            onRefresh = { series?.let { viewModel.seriesReplay.value += 1 } },
            onDismissRequest = { viewModel.series.value = null }
        )
        if (!tv) {
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
                onFavouriteStream = { stream ->
                    viewModel.favourite(stream.id)
                    mediaSheetValue = MediaSheetValue.FavouriteScreen()
                },
                onCreateStreamShortcut = { stream ->
                    viewModel.createShortcut(context, stream.id)
                    mediaSheetValue = MediaSheetValue.FavouriteScreen()
                },
                onDismissRequest = {
                    mediaSheetValue = MediaSheetValue.FavouriteScreen()
                    mediaSheetValue = MediaSheetValue.FavouriteScreen()
                }
            )
        } else {
            TvSortFullScreenDialog(
                visible = (mediaSheetValue as? MediaSheetValue.FavouriteScreen)?.stream != null,
                sort = sort,
                sorts = sorts,
                onChanged = { viewModel.sort(it) },
                onDismissRequest = { mediaSheetValue = MediaSheetValue.FavouriteScreen() }
            )
        }
    }
}

@Composable
private fun FavoriteScreen(
    contentPadding: PaddingValues,
    rowCount: Int,
    streamsResource: Resource<List<Stream>>,
    zapping: Stream?,
    recently: Boolean,
    onClickStream: (Stream) -> Unit,
    onLongClickStream: (Stream) -> Unit,
    onClickRandomTips: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val actualRowCount = when (configuration.orientation) {
        Configuration.ORIENTATION_PORTRAIT -> rowCount
        Configuration.ORIENTATION_LANDSCAPE -> rowCount + 2
        else -> rowCount + 2
    }
    FavouriteGallery(
        contentPadding = contentPadding,
        streamsResource = streamsResource,
        zapping = zapping,
        recently = recently,
        rowCount = actualRowCount,
        onClick = onClickStream,
        onLongClick = onLongClickStream,
        onClickRandomTips = onClickRandomTips,
        modifier = modifier.haze(
            LocalHazeState.current,
            HazeDefaults.style(MaterialTheme.colorScheme.surface)
        )
    )
}