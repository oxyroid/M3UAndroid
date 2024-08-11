package com.m3u.feature.favorite

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
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.core.util.basic.title
import com.m3u.core.wrapper.Resource
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.isSeries
import com.m3u.data.service.MediaCommand
import com.m3u.feature.favorite.components.FavouriteGallery
import com.m3u.i18n.R
import com.m3u.material.ktx.interceptVolumeEvent
import com.m3u.material.ktx.tv
import com.m3u.material.ktx.thenIf
import com.m3u.material.model.LocalHazeState
import com.m3u.ui.EpisodesBottomSheet
import com.m3u.ui.MediaSheet
import com.m3u.ui.MediaSheetValue
import com.m3u.ui.Sort
import com.m3u.ui.SortBottomSheet
import com.m3u.ui.TvSortFullScreenDialog
import com.m3u.ui.helper.Action
import com.m3u.ui.helper.LocalHelper
import com.m3u.ui.helper.Metadata
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.launch

@Composable
fun FavouriteRoute(
    navigateToChannel: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: FavouriteViewModel = hiltViewModel()
) {
    val tv = tv()

    val title = stringResource(R.string.ui_title_favourite)

    val helper = LocalHelper.current
    val preferences = hiltPreferences()
    val context = LocalContext.current

    val coroutineScope = rememberCoroutineScope()

    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val zapping by viewModel.zapping.collectAsStateWithLifecycle()
    val sorts = viewModel.sorts
    val sort by viewModel.sort.collectAsStateWithLifecycle()

    val sheetState = rememberModalBottomSheetState()

    var isSortSheetVisible by rememberSaveable { mutableStateOf(false) }
    var mediaSheetValue: MediaSheetValue.FavouriteScreen by remember {
        mutableStateOf(MediaSheetValue.FavouriteScreen())
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
        onLongClickChannel = { mediaSheetValue = MediaSheetValue.FavouriteScreen(it) },
        onClickRandomTips = {
            viewModel.playRandomly()
            navigateToChannel()
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
            onFavouriteChannel = { channel ->
                viewModel.favourite(channel.id)
                mediaSheetValue = MediaSheetValue.FavouriteScreen()
            },
            onCreateShortcut = { channel ->
                viewModel.createShortcut(context, channel.id)
                mediaSheetValue = MediaSheetValue.FavouriteScreen()
            },
            onDismissRequest = {
                mediaSheetValue = MediaSheetValue.FavouriteScreen()
                mediaSheetValue = MediaSheetValue.FavouriteScreen()
            }
        )
    } else {
        TvSortFullScreenDialog(
            visible = (mediaSheetValue as? MediaSheetValue.FavouriteScreen)?.channel != null,
            sort = sort,
            sorts = sorts,
            onChanged = { viewModel.sort(it) },
            onDismissRequest = { mediaSheetValue = MediaSheetValue.FavouriteScreen() }
        )
    }
}

@Composable
private fun FavoriteScreen(
    contentPadding: PaddingValues,
    rowCount: Int,
    channels: Resource<List<Channel>>,
    zapping: Channel?,
    recently: Boolean,
    onClickChannel: (Channel) -> Unit,
    onLongClickChannel: (Channel) -> Unit,
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
        channels = channels,
        zapping = zapping,
        recently = recently,
        rowCount = actualRowCount,
        onClick = onClickChannel,
        onLongClick = onLongClickChannel,
        onClickRandomTips = onClickRandomTips,
        modifier = modifier.haze(
            LocalHazeState.current,
            HazeDefaults.style(MaterialTheme.colorScheme.surface)
        )
    )
}