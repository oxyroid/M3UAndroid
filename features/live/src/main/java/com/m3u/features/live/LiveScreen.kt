package com.m3u.features.live

import android.graphics.Rect
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.util.lerp
import androidx.core.util.Consumer
import androidx.core.view.WindowInsetsCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.m3u.core.annotation.ClipMode
import com.m3u.core.util.basic.isNotEmpty
import com.m3u.core.util.context.toast
import com.m3u.features.live.components.DlnaDevicesBottomSheet
import com.m3u.features.live.fragments.LiveFragment
import com.m3u.ui.components.MaskState
import com.m3u.ui.components.rememberMaskState
import com.m3u.ui.ktx.EventHandler
import com.m3u.ui.ktx.LifecycleEffect
import com.m3u.ui.model.LocalHelper
import com.m3u.ui.model.LocalTheme
import com.m3u.ui.model.repeatOnLifecycle
import kotlin.math.absoluteValue

@Composable
internal fun LiveRoute(
    init: LiveEvent.Init,
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit,
    viewModel: LiveViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val helper = LocalHelper.current

    val state: LiveState by viewModel.state.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val isDevicesVisible by viewModel.isDevicesVisible.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()

    val maskState = rememberMaskState { visible ->
        helper.detectWindowInsetController {
            hide(WindowInsetsCompat.Type.navigationBars())
            if (visible) show(WindowInsetsCompat.Type.statusBars())
            else hide(WindowInsetsCompat.Type.statusBars())
        }
    }
    var isPipMode by remember { mutableStateOf(false) }

    LifecycleEffect { event ->
        when (event) {
            Lifecycle.Event.ON_STOP -> {
                if (isPipMode) {
                    viewModel.onEvent(LiveEvent.UninstallMedia)
                }
            }

            else -> {}
        }
    }

    helper.repeatOnLifecycle {
        maskState.active()
        onUserLeaveHint = {
            if (state.playerState.videoSize.isNotEmpty) {
                maskState.sleep()
                helper.enterPipMode(state.playerState.videoSize)
            }
        }
        statusBarsVisibility = false
        onPipModeChanged = Consumer { info ->
            isPipMode = info.isInPictureInPictureMode
        }
    }

    EventHandler(state.message) {
        context.toast(it)
    }

    LaunchedEffect(init) {
        viewModel.onEvent(init)
    }

    // TODO: replace with material3-carousel.
    DlnaDevicesBottomSheet(
        maskState = maskState,
        searching = searching,
        isDevicesVisible = isDevicesVisible,
        devices = devices,
        connectedDevices = state.connectedDevices,
        connectDlnaDevice = { viewModel.onEvent(LiveEvent.ConnectDlnaDevice(it)) },
        disconnectDlnaDevice = { viewModel.onEvent(LiveEvent.DisconnectDlnaDevice(it)) },
        onDismiss = { viewModel.onEvent(LiveEvent.ClearDlnaDevices) }
    )

    LiveScreen(
        init = state.init,
        experimentalMode = state.experimentalMode,
        clipMode = state.clipMode,
        fullInfoPlayer = state.fullInfoPlayer,
        recording = state.recording,
        searchDlnaDevices = { viewModel.onEvent(LiveEvent.SearchDlnaDevices) },
        onRecord = { viewModel.onEvent(LiveEvent.Record) },
        onFavourite = { viewModel.onEvent(LiveEvent.OnFavourite(it)) },
        onBackPressed = onBackPressed,
        maskState = maskState,
        player = state.player,
        playback = state.playerState.playback,
        videoSize = state.playerState.videoSize,
        playerError = state.playerState.playerError,
        onInstallMedia = { viewModel.onEvent(LiveEvent.InstallMedia(it)) },
        onUninstallMedia = { viewModel.onEvent(LiveEvent.UninstallMedia) },
        muted = state.muted,
        onMuted = { viewModel.onEvent(LiveEvent.OnMuted) },
        modifier = modifier
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiveScreen(
    init: LiveState.Init,
    @ClipMode clipMode: Int,
    fullInfoPlayer: Boolean,
    recording: Boolean,
    searchDlnaDevices: () -> Unit,
    onRecord: () -> Unit,
    onFavourite: (String) -> Unit,
    onBackPressed: () -> Unit,
    maskState: MaskState,
    experimentalMode: Boolean,
    player: Player?,
    playback: @Player.State Int,
    videoSize: Rect,
    playerError: PlaybackException?,
    onInstallMedia: (String) -> Unit,
    onUninstallMedia: () -> Unit,
    muted: Boolean,
    onMuted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalTheme.current
    when (init) {
        is LiveState.InitSingle -> {
            val live = init.live
            val url = live?.url.orEmpty()
            val favourite = live?.favourite ?: false
            LiveFragment(
                player = player,
                playback = playback,
                videoSize = videoSize,
                playerError = playerError,
                title = init.live?.title.orEmpty(),
                url = url,
                cover = init.live?.cover.orEmpty(),
                feedTitle = init.feed?.title.orEmpty(),
                maskState = maskState,
                experimentalMode = experimentalMode,
                fullInfoPlayer = fullInfoPlayer,
                clipMode = clipMode,
                recording = recording,
                stared = favourite,
                onRecord = onRecord,
                onFavourite = { onFavourite(url) },
                searchDlnaDevices = searchDlnaDevices,
                onBackPressed = onBackPressed,
                onInstallMedia = onInstallMedia,
                onUninstallMedia = onUninstallMedia,
                muted = muted,
                onMuted = onMuted,
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .testTag("features:live")
            )
        }

        is LiveState.InitPlayList -> {
            // TODO: move pager into mask
            val pagerState = rememberPagerState(init.initialIndex) { init.lives.size }
            VerticalPager(
                state = pagerState,
                modifier = modifier
                    .fillMaxSize()
                    .background(theme.background)
            ) { pageIndex ->
                val live = init.lives[pageIndex]
                val url = live.url
                val favourite = live.favourite
                LiveFragment(
                    player = player,
                    playback = playback,
                    videoSize = videoSize,
                    playerError = playerError,
                    title = live.title,
                    feedTitle = init.feed?.title.orEmpty(),
                    url = url,
                    cover = live.cover.orEmpty(),
                    maskState = maskState,
                    experimentalMode = experimentalMode,
                    fullInfoPlayer = fullInfoPlayer,
                    clipMode = clipMode,
                    recording = recording,
                    stared = favourite,
                    onRecord = onRecord,
                    onFavourite = { onFavourite(url) },
                    searchDlnaDevices = searchDlnaDevices,
                    onBackPressed = onBackPressed,
                    onInstallMedia = onInstallMedia,
                    onUninstallMedia = onUninstallMedia,
                    muted = muted,
                    onMuted = onMuted,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .scrollableGroup(
                            current = pagerState.currentPage - pageIndex,
                            offsetFraction = pagerState.currentPageOffsetFraction
                        )
                )
            }
        }
    }
}

private fun Modifier.scrollableGroup(
    current: Int,
    offsetFraction: Float
): Modifier = graphicsLayer {
    val offset = current + offsetFraction
        .absoluteValue
        .coerceIn(0f, 1f)
    val scale = lerp(1f, 0.8f, offset)
    scaleX = scale
    scaleY = scale
}
