package com.m3u.features.stream

import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.core.unspecified.unspecifiable
import com.m3u.core.util.basic.isNotEmpty
import com.m3u.core.util.basic.title
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.Stream
import com.m3u.features.stream.components.DlnaDevicesBottomSheet
import com.m3u.features.stream.components.FormatsBottomSheet
import com.m3u.features.stream.fragments.StreamFragment
import com.m3u.i18n.R.string
import com.m3u.material.components.Background
import com.m3u.material.components.mask.MaskInterceptor
import com.m3u.material.components.mask.MaskState
import com.m3u.material.components.mask.rememberMaskState
import com.m3u.ui.helper.LocalHelper
import com.m3u.ui.helper.OnPipModeChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Composable
fun StreamRoute(
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit,
    viewModel: StreamViewModel = hiltViewModel(),
) {
    val openInExternalPlayerString = stringResource(string.feat_stream_open_in_external_app)

    val helper = LocalHelper.current
    val pref = LocalPref.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val state: StreamState by viewModel.state.collectAsStateWithLifecycle()
    val playerState: StreamState.PlayerState by viewModel.playerState.collectAsStateWithLifecycle()
    val stream by viewModel.stream.collectAsStateWithLifecycle()
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val isDevicesVisible by viewModel.isDevicesVisible.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()

    val formats by viewModel.formats.collectAsStateWithLifecycle()
    val selectedFormats by viewModel.selectedFormats.collectAsStateWithLifecycle()

    val volume by viewModel.volume.collectAsStateWithLifecycle()
    val recording by viewModel.recording.collectAsStateWithLifecycle()

    var brightness by rememberSaveable { mutableFloatStateOf(helper.brightness) }
    var isPipMode by rememberSaveable { mutableStateOf(false) }
    var isAutoZappingMode by rememberSaveable { mutableStateOf(true) }
    var choosing by rememberSaveable { mutableStateOf(false) }

    val maskState = rememberMaskState()

    LifecycleResumeEffect {
        with(helper) {
            isSystemBarUseDarkMode = true.unspecifiable
            statusBarVisibility = false.unspecifiable
            navigationBarVisibility = false.unspecifiable
            onPipModeChanged = OnPipModeChanged { info ->
                isPipMode = info.isInPictureInPictureMode
                if (!isPipMode) {
                    maskState.wake()
                    isAutoZappingMode = false
                }
            }
        }
        onPauseOrDispose {
            viewModel.onEvent(StreamEvent.CloseDlnaDevices)
        }
    }

    LaunchedEffect(pref.zappingMode, playerState.videoSize) {
        val videoSize = playerState.videoSize
        if (isAutoZappingMode && pref.zappingMode && !isPipMode) {
            maskState.sleep()
            val rect = if (videoSize.isNotEmpty) videoSize
            else Rect(0, 0, 1920, 1080)
            helper.enterPipMode(rect)
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { brightness }
            .onEach { helper.brightness = it }
            .launchIn(this)

        snapshotFlow { maskState.visible }
            .onEach { visible ->
                helper.statusBarVisibility = visible.unspecifiable
                helper.navigationBarVisibility = false.unspecifiable
            }
            .launchIn(this)
    }

    DisposableEffect(Unit) {
        val prev = helper.brightness
        onDispose {
            helper.brightness = prev
        }
    }

    LaunchedEffect(isPipMode) {
        val interceptor: MaskInterceptor? = if (isPipMode) { _ -> false } else null
        maskState.intercept(interceptor)
    }

    Background(
        color = Color.Black,
        contentColor = Color.White
    ) {
        DlnaDevicesBottomSheet(
            maskState = maskState,
            searching = searching,
            isDevicesVisible = isDevicesVisible,
            devices = devices,
            connected = state.connected,
            connectDlnaDevice = { viewModel.onEvent(StreamEvent.ConnectDlnaDevice(it)) },
            disconnectDlnaDevice = { viewModel.onEvent(StreamEvent.DisconnectDlnaDevice(it)) },
            openInExternalPlayer = {
                val streamUrl = stream?.url ?: return@DlnaDevicesBottomSheet
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(streamUrl), "video/*")
                    }.let { Intent.createChooser(it, openInExternalPlayerString.title()) }
                )
                viewModel.openInExternalPlayer()
            },
            onDismiss = { viewModel.onEvent(StreamEvent.CloseDlnaDevices) }
        )

        FormatsBottomSheet(
            visible = choosing,
            formats = formats,
            selectedFormats = selectedFormats,
            maskState = maskState,
            onDismiss = { choosing = false },
            onChooseTrack = { type, format ->
                viewModel.chooseTrack(type, format)
            },
            onClearTrack = { type ->
                viewModel.clearTrack(type)
            }
        )

        StreamScreen(
            recording = recording,
            openDlnaDevices = { viewModel.onEvent(StreamEvent.OpenDlnaDevices) },
            openChooseFormat = { choosing = true },
            onRecord = { viewModel.record() },
            onFavourite = { viewModel.onEvent(StreamEvent.OnFavourite) },
            onBackPressed = onBackPressed,
            maskState = maskState,
            playerState = playerState,
            playlist = playlist,
            stream = stream,
            formatsIsNotEmpty = formats.isNotEmpty(),
            brightness = brightness,
            volume = volume,
            onBrightness = { brightness = it },
            onVolume = { viewModel.onEvent(StreamEvent.OnVolume(it)) },
            replay = { coroutineScope.launch { helper.replay() } },
            modifier = modifier
        )
    }
}

@Composable
private fun StreamScreen(
    recording: Boolean,
    onRecord: () -> Unit,
    onFavourite: () -> Unit,
    onBackPressed: () -> Unit,
    maskState: MaskState,
    playerState: StreamState.PlayerState,
    playlist: Playlist?,
    stream: Stream?,
    formatsIsNotEmpty: Boolean,
    openDlnaDevices: () -> Unit,
    openChooseFormat: () -> Unit,
    volume: Float,
    brightness: Float,
    onVolume: (Float) -> Unit,
    onBrightness: (Float) -> Unit,
    replay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stream?.title ?: "--"
    val cover = stream?.cover.orEmpty()
    val playlistTitle = playlist?.title ?: "--"
    val favourite = stream?.favourite ?: false

    StreamFragment(
        playerState = playerState,
        title = title,
        cover = cover,
        formatsIsNotEmpty = formatsIsNotEmpty,
        playlistTitle = playlistTitle,
        maskState = maskState,
        recording = recording,
        favourite = favourite,
        onRecord = onRecord,
        onFavourite = onFavourite,
        openDlnaDevices = openDlnaDevices,
        openChooseFormat = openChooseFormat,
        onBackPressed = onBackPressed,
        replay = replay,
        brightness = brightness,
        volume = volume,
        onBrightness = onBrightness,
        onVolume = onVolume,
        modifier = modifier
            .fillMaxSize()
            .testTag("features:stream")
    )
}
