package com.m3u.features.stream

import android.graphics.Rect
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Format
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.core.unspecified.unspecifiable
import com.m3u.core.util.basic.isNotEmpty
import com.m3u.features.stream.components.DlnaDevicesBottomSheet
import com.m3u.features.stream.components.FormatsBottomSheet
import com.m3u.features.stream.components.rememberDeviceWrapper
import com.m3u.features.stream.fragments.AudioBecomingNoisyReceiver
import com.m3u.features.stream.fragments.StreamFragment
import com.m3u.material.components.Background
import com.m3u.material.components.mask.MaskInterceptor
import com.m3u.material.components.mask.MaskState
import com.m3u.material.components.mask.rememberMaskState
import com.m3u.material.ktx.isTvDevice
import com.m3u.ui.LocalHelper
import com.m3u.ui.OnPipModeChanged
import com.m3u.ui.repeatOnLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
fun StreamRoute(
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit,
    viewModel: StreamViewModel = hiltViewModel(),
) {
    val helper = LocalHelper.current
    val pref = LocalPref.current
    val context = LocalContext.current

    val state: StreamState by viewModel.state.collectAsStateWithLifecycle()
    val playerState: StreamState.PlayerState by viewModel.playerState.collectAsStateWithLifecycle()
    val metadata: StreamState.Metadata by viewModel.metadata.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val isDevicesVisible by viewModel.isDevicesVisible.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()
    val formats by viewModel.videoFormats.collectAsStateWithLifecycle()
    val format by viewModel.format.collectAsStateWithLifecycle()

    val volume by viewModel.volume.collectAsStateWithLifecycle()
    var brightness by rememberSaveable { mutableFloatStateOf(helper.brightness) }
    var isPipMode by rememberSaveable { mutableStateOf(false) }
    var isAutoZappingMode by rememberSaveable { mutableStateOf(true) }
    var choosing by rememberSaveable { mutableStateOf(false) }

    val maskState = rememberMaskState()

    LifecycleStartEffect {
        onStopOrDispose {
            if (isPipMode) {
                viewModel.onEvent(StreamEvent.Stop)
            }
        }
    }

    helper.repeatOnLifecycle {
        darkMode = true.unspecifiable
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
    }

    LaunchedEffect(Unit) {
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
    LifecycleStartEffect {
        val receiver = AudioBecomingNoisyReceiver {
            maskState.wake()
            viewModel.onEvent(StreamEvent.OnVolume(0f))
        }
        try {
            ContextCompat.registerReceiver(
                context,
                receiver,
                AudioBecomingNoisyReceiver.INTENT_FILTER,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (ignored: Exception) {
        }
        onStopOrDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (ignored: Exception) {
            }
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
            connected = rememberDeviceWrapper(state.connected),
            connectDlnaDevice = { viewModel.onEvent(StreamEvent.ConnectDlnaDevice(it)) },
            disconnectDlnaDevice = { viewModel.onEvent(StreamEvent.DisconnectDlnaDevice(it)) },
            onDismiss = { viewModel.onEvent(StreamEvent.CloseDlnaDevices) }
        )

        FormatsBottomSheet(
            visible = choosing,
            formats = formats,
            format = format,
            maskState = maskState,
            onDismiss = { choosing = false },
            onClick = { viewModel.chooseFormat(it) }
        )

        StreamScreen(
            recording = state.recording,
            openDlnaDevices = { viewModel.onEvent(StreamEvent.OpenDlnaDevices) },
            openChooseFormat = { choosing = true },
            onRecord = { viewModel.onEvent(StreamEvent.Record) },
            onFavourite = { viewModel.onEvent(StreamEvent.OnFavourite(it)) },
            onBackPressed = onBackPressed,
            maskState = maskState,
            playerState = playerState,
            metadata = metadata,
            formats = formats,
            brightness = brightness,
            volume = volume,
            onBrightness = { brightness = it },
            onVolume = { viewModel.onEvent(StreamEvent.OnVolume(it)) },
            replay = { helper.replay() },
            modifier = modifier
        )
    }
}

@Composable
private fun StreamScreen(
    recording: Boolean,
    onRecord: () -> Unit,
    onFavourite: (String) -> Unit,
    onBackPressed: () -> Unit,
    maskState: MaskState,
    playerState: StreamState.PlayerState,
    metadata: StreamState.Metadata,
    formats: ImmutableList<Format>,
    openDlnaDevices: () -> Unit,
    openChooseFormat: () -> Unit,
    volume: Float,
    brightness: Float,
    onVolume: (Float) -> Unit,
    onBrightness: (Float) -> Unit,
    replay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stream = metadata.stream
    val playlist = metadata.playlist

    val url = stream?.url.orEmpty()
    val title = stream?.title ?: "--"
    val cover = stream?.cover.orEmpty()
    val playlistTitle = playlist?.title ?: "--"
    val favourite = stream?.favourite ?: false

    BackHandler(isTvDevice() && maskState.visible) {
        maskState.sleep()
    }

    StreamFragment(
        playerState = playerState,
        title = title,
        url = url,
        cover = cover,
        formats = formats,
        playlistTitle = playlistTitle,
        maskState = maskState,
        recording = recording,
        favourite = favourite,
        onRecord = onRecord,
        onFavourite = { onFavourite(url) },
        openDlnaDevices = openDlnaDevices,
        openChooseFormat = openChooseFormat,
        onBackPressed = onBackPressed,
        replay = replay,
        brightness = brightness,
        volume = volume,
        onBrightness = onBrightness,
        onVolume = onVolume,
        windowInsets = WindowInsets.statusBars,
        modifier = modifier
            .fillMaxSize()
            .testTag("features:stream")
    )
}
