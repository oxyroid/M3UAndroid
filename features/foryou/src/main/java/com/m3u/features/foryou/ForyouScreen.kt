package com.m3u.features.foryou

import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
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
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.core.util.basic.title
import com.m3u.core.wrapper.Resource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.PlaylistWithCount
import com.m3u.data.database.model.Stream
import com.m3u.data.service.MediaCommand
import com.m3u.features.foryou.components.ForyouDialog
import com.m3u.features.foryou.components.ForyouDialogState
import com.m3u.features.foryou.components.PlaylistGallery
import com.m3u.features.foryou.components.PlaylistGalleryPlaceholder
import com.m3u.features.foryou.components.recommend.Recommend
import com.m3u.features.foryou.components.recommend.RecommendGallery
import com.m3u.i18n.R.string
import com.m3u.material.components.Background
import com.m3u.material.ktx.interceptVolumeEvent
import com.m3u.material.ktx.isTelevision
import com.m3u.material.ktx.thenIf
import com.m3u.material.model.LocalHazeState
import com.m3u.ui.Destination
import com.m3u.ui.EpisodesBottomSheet
import com.m3u.ui.LocalVisiblePageInfos
import com.m3u.ui.helper.Action
import com.m3u.ui.helper.LocalHelper
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.haze
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch

@Composable
fun ForyouRoute(
    navigateToPlaylist: (Playlist) -> Unit,
    navigateToStream: () -> Unit,
    navigateToSettingPlaylistManagement: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: ForyouViewModel = hiltViewModel()
) {
    val helper = LocalHelper.current
    val pref = LocalPref.current
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

    if (isPageInfoVisible) {
        LifecycleResumeEffect(title) {
            helper.title = title.title()
            helper.actions = persistentListOf(
                Action(
                    icon = Icons.Rounded.Add,
                    contentDescription = "add",
                    onClick = navigateToSettingPlaylistManagement
                )
            )
            onPauseOrDispose {
                helper.actions = persistentListOf()
            }
        }
    }

    Background {
        Box(modifier) {
            ForyouScreen(
                playlistCountsResource = playlistCountsResource,
                recommend = recommend,
                rowCount = pref.rowCount,
                contentPadding = contentPadding,
                navigateToPlaylist = navigateToPlaylist,
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
                navigateToSettingPlaylistManagement = navigateToSettingPlaylistManagement,
                unsubscribe = { viewModel.unsubscribe(it) },
                rename = { playlistUrl, target -> viewModel.rename(playlistUrl, target) },
                updateUserAgent = { playlistUrl, userAgent ->
                    viewModel.updateUserAgent(
                        playlistUrl,
                        userAgent
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .thenIf(!tv && pref.godMode) {
                        Modifier.interceptVolumeEvent { event ->
                            pref.rowCount = when (event) {
                                KeyEvent.KEYCODE_VOLUME_UP -> (pref.rowCount - 1).coerceAtLeast(1)
                                KeyEvent.KEYCODE_VOLUME_DOWN -> (pref.rowCount + 1).coerceAtMost(2)
                                else -> return@interceptVolumeEvent
                            }
                        }
                    }
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
        }
    }
}

@Composable
private fun ForyouScreen(
    rowCount: Int,
    playlistCountsResource: Resource<ImmutableList<PlaylistWithCount>>,
    recommend: Recommend,
    contentPadding: PaddingValues,
    navigateToPlaylist: (Playlist) -> Unit,
    onClickStream: (Stream) -> Unit,
    navigateToSettingPlaylistManagement: () -> Unit,
    unsubscribe: (playlistUrl: String) -> Unit,
    rename: (playlistUrl: String, label: String) -> Unit,
    updateUserAgent: (playlistUrl: String, userAgent: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current

    val actualRowCount = remember(rowCount, configuration.orientation) {
        when (configuration.orientation) {
            ORIENTATION_PORTRAIT -> rowCount
            else -> rowCount + 2
        }
    }
    var dialogState: ForyouDialogState by remember { mutableStateOf(ForyouDialogState.Idle) }
    Background(modifier) {
        Box {
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
                            onClick = navigateToPlaylist,
                            onLongClick = { dialogState = ForyouDialogState.Selections(it) },
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
                    ForyouDialog(
                        status = dialogState,
                        update = { dialogState = it },
                        unsubscribe = unsubscribe,
                        rename = rename,
                        editUserAgent = updateUserAgent
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

    BackHandler(dialogState != ForyouDialogState.Idle) {
        dialogState = ForyouDialogState.Idle
    }
}
