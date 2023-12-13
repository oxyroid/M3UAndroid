package com.m3u.features.live

import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.core.unspecified.UBoolean
import com.m3u.core.unspecified.unspecifiable
import com.m3u.core.util.basic.isNotEmpty
import com.m3u.features.live.components.DlnaDevicesBottomSheet
import com.m3u.features.live.components.rememberDeviceHolder
import com.m3u.features.live.components.rememberDeviceWrapper
import com.m3u.features.live.fragments.LiveFragment
import com.m3u.material.components.Background
import com.m3u.material.components.Interceptor
import com.m3u.material.components.MaskState
import com.m3u.material.components.rememberMaskState
import com.m3u.material.ktx.LifecycleEffect
import com.m3u.ui.LocalHelper
import com.m3u.ui.OnPipModeChanged
import com.m3u.ui.repeatOnLifecycle
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
fun LiveRoute(
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit,
    viewModel: LiveViewModel = hiltViewModel(),
) {
    val helper = LocalHelper.current
    val pref = LocalPref.current

    val state: LiveState by viewModel.state.collectAsStateWithLifecycle()
    val playerState: LiveState.PlayerState by viewModel.playerState.collectAsStateWithLifecycle()
    val metadata: LiveState.Metadata by viewModel.metadata.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val isDevicesVisible by viewModel.isDevicesVisible.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()

    val volume by viewModel.volume.collectAsStateWithLifecycle()
    var brightness by rememberSaveable { mutableFloatStateOf(helper.brightness) }
    var isPipMode by rememberSaveable { mutableStateOf(false) }
    var isAutoZappingMode by rememberSaveable { mutableStateOf(true) }

    val maskState = rememberMaskState()

    LifecycleEffect { event ->
        when (event) {
            Lifecycle.Event.ON_STOP -> {
                if (isPipMode) {
                    viewModel.onEvent(LiveEvent.Stop)
                }
            }
            else -> {}
        }
    }

    helper.repeatOnLifecycle {
        darkMode = true.unspecifiable
        statusBarVisibility = UBoolean.False
        navigationBarVisibility = UBoolean.False
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
                helper.navigationBarVisibility = UBoolean.False
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
        val interceptor: Interceptor? = if (isPipMode) { _ -> false } else null
        maskState.intercept(interceptor)
    }

    Background(
        color = Color.Black,
        contentColor = Color.White
    ) {
        // TODO: replace with material3-carousel.
        DlnaDevicesBottomSheet(
            maskState = maskState,
            searching = searching,
            isDevicesVisible = isDevicesVisible,
            deviceHolder = rememberDeviceHolder(devices),
            connected = rememberDeviceWrapper(state.connected),
            connectDlnaDevice = { viewModel.onEvent(LiveEvent.ConnectDlnaDevice(it)) },
            disconnectDlnaDevice = { viewModel.onEvent(LiveEvent.DisconnectDlnaDevice(it)) },
            onDismiss = { viewModel.onEvent(LiveEvent.CloseDlnaDevices) }
        )

        LiveScreen(
            recording = state.recording,
            openDlnaDevices = { viewModel.onEvent(LiveEvent.OpenDlnaDevices) },
            onRecord = { viewModel.onEvent(LiveEvent.Record) },
            onFavourite = { viewModel.onEvent(LiveEvent.OnFavourite(it)) },
            onBackPressed = onBackPressed,
            maskState = maskState,
            playerState = playerState,
            metadata = metadata,
            brightness = brightness,
            volume = volume,
            onBrightness = { brightness = it },
            onVolume = { viewModel.onEvent(LiveEvent.OnVolume(it)) },
            replay = { helper.replay() },
            modifier = modifier
        )
    }
}

@Composable
private fun LiveScreen(
    recording: Boolean,
    openDlnaDevices: () -> Unit,
    onRecord: () -> Unit,
    onFavourite: (String) -> Unit,
    onBackPressed: () -> Unit,
    maskState: MaskState,
    playerState: LiveState.PlayerState,
    metadata: LiveState.Metadata,
    volume: Float,
    brightness: Float,
    onVolume: (Float) -> Unit,
    onBrightness: (Float) -> Unit,
    replay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val live = metadata.live
    val feed = metadata.feed
    val url = live?.url.orEmpty()
    val title = live?.title ?: "--"
    val cover = live?.cover.orEmpty()
    val feedTitle = feed?.title ?: "--"
    val favourite = live?.favourite ?: false
    LiveFragment(
        playerState = playerState,
        title = title,
        url = url,
        cover = cover,
        feedTitle = feedTitle,
        maskState = maskState,
        recording = recording,
        favourite = favourite,
        onRecord = onRecord,
        onFavourite = { onFavourite(url) },
        openDlnaDevices = openDlnaDevices,
        onBackPressed = onBackPressed,
        replay = replay,
        brightness = brightness,
        volume = volume,
        onBrightness = onBrightness,
        onVolume = onVolume,
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("features:live")
    )
}
