package com.m3u.features.foryou

import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.core.util.basic.title
import com.m3u.core.wrapper.Resource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.PlaylistWithCount
import com.m3u.data.database.model.Stream
import com.m3u.data.database.model.isSeries
import com.m3u.data.service.MediaCommand
import com.m3u.features.foryou.components.PlaylistGallery
import com.m3u.features.foryou.components.PlaylistGalleryPlaceholder
import com.m3u.features.foryou.components.recommend.Recommend
import com.m3u.features.foryou.components.recommend.RecommendGallery
import com.m3u.i18n.R.string
import com.m3u.material.ktx.interceptVolumeEvent
import com.m3u.material.ktx.isTelevision
import com.m3u.material.ktx.thenIf
import com.m3u.material.model.LocalHazeState
import com.m3u.ui.Destination
import com.m3u.ui.EpisodesBottomSheet
import com.m3u.ui.LocalVisiblePageInfos
import com.m3u.ui.MediaSheet
import com.m3u.ui.MediaSheetValue
import com.m3u.ui.helper.Action
import com.m3u.ui.helper.LocalHelper
import com.m3u.ui.helper.Metadata
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.launch

@Composable
fun ForyouRoute(
    navigateToPlaylist: (Playlist) -> Unit,
    navigateToStream: () -> Unit,
    navigateToPlaylistConfiguration: (Playlist) -> Unit,
    navigateToSettingPlaylistManagement: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: ForyouViewModel = hiltViewModel()
) {
    val helper = LocalHelper.current
    val preferences = hiltPreferences()
    val visiblePageInfos = LocalVisiblePageInfos.current
    val coroutineScope = rememberCoroutineScope()

    val tv = isTelevision()
    val title = stringResource(string.ui_title_foryou)

    val pageIndex = remember { Destination.Root.entries.indexOf(Destination.Root.Foryou) }
    val isPageInfoVisible = remember(pageIndex, visiblePageInfos) {
        visiblePageInfos.find { it.index == pageIndex } != null
    }

    val playlistCountsResource by viewModel.playlistCountsResource.collectAsStateWithLifecycle()
    val recommend by viewModel.recommend.collectAsStateWithLifecycle()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()

    val series: Stream? by viewModel.series.collectAsStateWithLifecycle()
    val subscribingPlaylistUrls by
    viewModel.subscribingPlaylistUrls.collectAsStateWithLifecycle()

    if (isPageInfoVisible) {
        LifecycleResumeEffect(title) {
            Metadata.title = title.title()
            Metadata.actions = listOf(
                Action(
                    icon = Icons.Rounded.Add,
                    contentDescription = "add",
                    onClick = navigateToSettingPlaylistManagement
                )
            )
            onPauseOrDispose {
                Metadata.actions = emptyList()
            }
        }
    }

    ForyouScreen(
        playlistCountsResource = playlistCountsResource,
        subscribingPlaylistUrls = subscribingPlaylistUrls,
        recommend = recommend,
        rowCount = preferences.rowCount,
        contentPadding = contentPadding,
        navigateToPlaylist = navigateToPlaylist,
        onClickStream = { stream ->
            coroutineScope.launch {
                val playlist = viewModel.getPlaylist(stream.playlistUrl)
                when {
                    playlist?.isSeries ?: false -> {
                        viewModel.series.value = stream
                    }

                    else -> {
                        helper.play(MediaCommand.Common(stream.id))
                        navigateToStream()
                    }
                }
            }
        },
        navigateToSettingPlaylistManagement = navigateToSettingPlaylistManagement,
        navigateToPlaylistConfiguration = navigateToPlaylistConfiguration,
        onUnsubscribePlaylist = viewModel::onUnsubscribePlaylist,
        modifier = Modifier
            .fillMaxSize()
            .thenIf(!tv && preferences.godMode) {
                Modifier.interceptVolumeEvent { event ->
                    preferences.rowCount = when (event) {
                        KeyEvent.KEYCODE_VOLUME_UP -> (preferences.rowCount - 1).coerceAtLeast(1)
                        KeyEvent.KEYCODE_VOLUME_DOWN -> (preferences.rowCount + 1).coerceAtMost(
                            2
                        )

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
                series?.let { stream ->
                    val input = MediaCommand.XtreamEpisode(
                        streamId = stream.id,
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
}

@Composable
private fun ForyouScreen(
    rowCount: Int,
    playlistCountsResource: Resource<List<PlaylistWithCount>>,
    subscribingPlaylistUrls: List<String>,
    recommend: Recommend,
    contentPadding: PaddingValues,
    navigateToPlaylist: (Playlist) -> Unit,
    onClickStream: (Stream) -> Unit,
    navigateToSettingPlaylistManagement: () -> Unit,
    navigateToPlaylistConfiguration: (Playlist) -> Unit,
    onUnsubscribePlaylist: (playlistUrl: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current

    val actualRowCount = remember(rowCount, configuration.orientation) {
        when (configuration.orientation) {
            ORIENTATION_PORTRAIT -> rowCount
            else -> rowCount + 2
        }
    }
    var mediaSheetValue: MediaSheetValue.ForyouScreen by remember {
        mutableStateOf(MediaSheetValue.ForyouScreen())
    }
    Box(modifier) {
        when (playlistCountsResource) {
            Resource.Loading -> {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(contentPadding)
                )
            }

            is Resource.Success -> {
                val showPlaylist = playlistCountsResource.data.isNotEmpty()
                val header = @Composable {
                    RecommendGallery(
                        recommend = recommend,
                        navigateToPlaylist = navigateToPlaylist,
                        onClickStream = onClickStream,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (showPlaylist) {
                    PlaylistGallery(
                        rowCount = actualRowCount,
                        playlistCounts = playlistCountsResource.data,
                        subscribingPlaylistUrls = subscribingPlaylistUrls,
                        onClick = navigateToPlaylist,
                        onLongClick = { mediaSheetValue = MediaSheetValue.ForyouScreen(it) },
                        header = header.takeIf { recommend.isNotEmpty() },
                        contentPadding = contentPadding,
                        modifier = Modifier
                            .fillMaxSize()
                            .haze(
                                LocalHazeState.current,
                                HazeDefaults.style(MaterialTheme.colorScheme.surface)
                            )
                    )
                } else {
                    Box(Modifier.fillMaxSize()) {
                        PlaylistGalleryPlaceholder(
                            navigateToSettingPlaylistManagement = navigateToSettingPlaylistManagement,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                MediaSheet(
                    value = mediaSheetValue,
                    onUnsubscribePlaylist = {
                        onUnsubscribePlaylist(it.url)
                        mediaSheetValue = MediaSheetValue.ForyouScreen()
                    },
                    onPlaylistConfiguration = navigateToPlaylistConfiguration,
                    onDismissRequest = {
                        mediaSheetValue = MediaSheetValue.ForyouScreen()
                    }
                )
            }

            is Resource.Failure -> {
                Text(
                    text = playlistCountsResource.message.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}
