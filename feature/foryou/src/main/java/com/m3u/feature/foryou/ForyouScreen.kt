package com.m3u.feature.foryou

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.core.util.basic.title
import com.m3u.core.wrapper.Resource
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.PlaylistWithCount
import com.m3u.data.database.model.isSeries
import com.m3u.data.service.MediaCommand
import com.m3u.feature.foryou.components.HeadlineBackground
import com.m3u.feature.foryou.components.PlaylistGallery
import com.m3u.feature.foryou.components.recommend.Recommend
import com.m3u.feature.foryou.components.recommend.RecommendGallery
import com.m3u.i18n.R.string
import com.m3u.material.ktx.interceptVolumeEvent
import com.m3u.material.ktx.tv
import com.m3u.material.ktx.thenIf
import com.m3u.ui.EpisodesBottomSheet
import com.m3u.ui.MediaSheet
import com.m3u.ui.MediaSheetValue
import com.m3u.ui.helper.Action
import com.m3u.ui.helper.LocalHelper
import com.m3u.ui.helper.Metadata
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun ForyouRoute(
    navigateToPlaylist: (Playlist) -> Unit,
    navigateToChannel: () -> Unit,
    navigateToPlaylistConfiguration: (Playlist) -> Unit,
    navigateToSettingPlaylistManagement: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: ForyouViewModel = hiltViewModel()
) {
    val helper = LocalHelper.current
    val preferences = hiltPreferences()
    val coroutineScope = rememberCoroutineScope()

    val tv = tv()
    val title = stringResource(string.ui_title_foryou)

    val playlistCounts by viewModel.playlistCounts.collectAsStateWithLifecycle()
    val specs by viewModel.specs.collectAsStateWithLifecycle()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()

    val series: Channel? by viewModel.series.collectAsStateWithLifecycle()
    val subscribingPlaylistUrls by
    viewModel.subscribingPlaylistUrls.collectAsStateWithLifecycle()
    val refreshingEpgUrls by viewModel.refreshingEpgUrls.collectAsStateWithLifecycle(emptyList())

    LifecycleResumeEffect(title) {
        Metadata.title = AnnotatedString(title.title())
        Metadata.color = Color.Unspecified
        Metadata.contentColor = Color.Unspecified
        Metadata.actions = listOf(
            Action(
                icon = Icons.Rounded.Add,
                contentDescription = "add",
                onClick = navigateToSettingPlaylistManagement
            )
        )
        onPauseOrDispose {
            Metadata.actions = emptyList()
            Metadata.headlineUrl = ""
        }
    }

    ForyouScreen(
        playlistCounts = playlistCounts,
        subscribingPlaylistUrls = subscribingPlaylistUrls,
        refreshingEpgUrls = refreshingEpgUrls,
        specs = specs,
        rowCount = preferences.rowCount,
        contentPadding = contentPadding,
        navigateToPlaylist = navigateToPlaylist,
        onPlayChannel = { channel ->
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
        navigateToPlaylistConfiguration = navigateToPlaylistConfiguration,
        onUnsubscribePlaylist = viewModel::onUnsubscribePlaylist,
        modifier = Modifier
            .fillMaxSize()
            .thenIf(!tv && preferences.godMode) {
                Modifier.interceptVolumeEvent { event ->
                    preferences.rowCount = when (event) {
                        KeyEvent.KEYCODE_VOLUME_UP -> (preferences.rowCount - 1).coerceAtLeast(1)
                        KeyEvent.KEYCODE_VOLUME_DOWN -> (preferences.rowCount + 1).coerceAtMost(2)

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
                series?.let { channel ->
                    val input = MediaCommand.XtreamEpisode(
                        channelId = channel.id,
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
}

@Composable
private fun ForyouScreen(
    rowCount: Int,
    playlistCounts: Resource<List<PlaylistWithCount>>,
    subscribingPlaylistUrls: List<String>,
    refreshingEpgUrls: List<String>,
    specs: List<Recommend.Spec>,
    contentPadding: PaddingValues,
    navigateToPlaylist: (Playlist) -> Unit,
    onPlayChannel: (Channel) -> Unit,
    navigateToPlaylistConfiguration: (Playlist) -> Unit,
    onUnsubscribePlaylist: (playlistUrl: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var headlineSpec: Recommend.Spec? by remember { mutableStateOf(null) }

    val actualRowCount = remember(rowCount, configuration.orientation) {
        when (configuration.orientation) {
            ORIENTATION_PORTRAIT -> rowCount
            else -> rowCount + 2
        }
    }
    var mediaSheetValue: MediaSheetValue.ForyouScreen by remember {
        mutableStateOf(MediaSheetValue.ForyouScreen())
    }

    LaunchedEffect(headlineSpec) {
        val spec = headlineSpec
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(400.milliseconds)
            Metadata.headlineUrl = when (spec) {
                is Recommend.UnseenSpec -> spec.channel.cover.orEmpty()
                is Recommend.DiscoverSpec -> ""
                is Recommend.NewRelease -> ""
                else -> ""
            }
        }
    }

    Box(modifier) {
        HeadlineBackground()
        when (playlistCounts) {
            Resource.Loading -> {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(contentPadding)
                )
            }

            is Resource.Success -> {
                val header = @Composable {
                    RecommendGallery(
                        specs = specs,
                        navigateToPlaylist = navigateToPlaylist,
                        onPlayChannel = onPlayChannel,
                        onSpecChanged = { spec -> headlineSpec = spec },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                PlaylistGallery(
                    rowCount = actualRowCount,
                    playlistCounts = playlistCounts.data,
                    subscribingPlaylistUrls = subscribingPlaylistUrls,
                    refreshingEpgUrls = refreshingEpgUrls,
                    onClick = navigateToPlaylist,
                    onLongClick = { mediaSheetValue = MediaSheetValue.ForyouScreen(it) },
                    header = header.takeIf { specs.isNotEmpty() },
                    contentPadding = contentPadding,
                    modifier = Modifier.fillMaxSize()
                )
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
                    text = playlistCounts.message.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}
