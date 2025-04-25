package com.m3u.smartphone.ui.business.channel

import android.Manifest
import android.content.Intent
import android.graphics.Rect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.util.fastRoundToInt
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.google.accompanist.permissions.rememberPermissionState
import com.m3u.business.channel.ChannelViewModel
import com.m3u.business.channel.PlayerState
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.preferenceOf
import com.m3u.core.util.basic.isNotEmpty
import com.m3u.core.util.basic.title
import com.m3u.data.database.model.AdjacentChannels
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.business.channel.components.DlnaDevicesBottomSheet
import com.m3u.smartphone.ui.business.channel.components.FormatsBottomSheet
import com.m3u.smartphone.ui.business.channel.components.MaskDimension
import com.m3u.smartphone.ui.business.channel.components.MaskGestureValuePanel
import com.m3u.smartphone.ui.business.channel.components.PlayerPanel
import com.m3u.smartphone.ui.business.channel.components.VerticalGestureArea
import com.m3u.smartphone.ui.common.helper.LocalHelper
import com.m3u.smartphone.ui.common.helper.OnPipModeChanged
import com.m3u.smartphone.ui.material.components.Player
import com.m3u.smartphone.ui.material.components.PullPanelLayout
import com.m3u.smartphone.ui.material.components.PullPanelLayoutDefaults
import com.m3u.smartphone.ui.material.components.mask.MaskInterceptor
import com.m3u.smartphone.ui.material.components.mask.MaskState
import com.m3u.smartphone.ui.material.components.mask.rememberMaskState
import com.m3u.smartphone.ui.material.components.mask.toggle
import com.m3u.smartphone.ui.material.components.rememberPlayerState
import com.m3u.smartphone.ui.material.components.rememberPullPanelLayoutState
import com.m3u.smartphone.ui.material.ktx.checkPermissionOrRationale
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.time.Duration.Companion.seconds

@Composable
fun ChannelRoute(
    modifier: Modifier = Modifier,
    viewModel: ChannelViewModel = hiltViewModel(),
) {
    val openInExternalPlayerString = stringResource(string.feat_channel_open_in_external_app)

    val helper = LocalHelper.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current

    val isPanelEnabled by preferenceOf(PreferencesKeys.PLAYER_PANEL)
    val zappingMode by preferenceOf(PreferencesKeys.ZAPPING_MODE)

    val requestIgnoreBatteryOptimizations =
        rememberPermissionState(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)

    val playerState: PlayerState by viewModel.playerState.collectAsStateWithLifecycle()
    val channel by viewModel.channel.collectAsStateWithLifecycle()
    val adjacentChannels by viewModel.adjacentChannels.collectAsStateWithLifecycle()
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val devices = viewModel.devices
    val isDevicesVisible by viewModel.isDevicesVisible.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()

    val tracks by viewModel.tracks.collectAsStateWithLifecycle(emptyMap())
    val selectedFormats by viewModel.currentTracks.collectAsStateWithLifecycle(emptyMap())

    val volume by viewModel.volume.collectAsStateWithLifecycle()
    val isSeriesPlaylist by viewModel.isSeriesPlaylist.collectAsStateWithLifecycle(false)
    val isProgrammeSupported by viewModel.isProgrammeSupported.collectAsStateWithLifecycle(
        initialValue = false
    )

    val channels = viewModel.pagingChannels.collectAsLazyPagingItems()
    val programmes = viewModel.programmes.collectAsLazyPagingItems()
    val programmeRange by viewModel.programmeRange.collectAsStateWithLifecycle()

    val programmeReminderIds by viewModel.programmeReminderIds.collectAsStateWithLifecycle()

    var brightness by remember { mutableFloatStateOf(helper.brightness) }
    var speed by remember { mutableFloatStateOf(1f) }
    var isPipMode by remember { mutableStateOf(false) }
    var isAutoZappingMode by remember { mutableStateOf(true) }
    var choosing by remember { mutableStateOf(false) }

    val useVertical = PullPanelLayoutDefaults.UseVertical

    val maskState = rememberMaskState()
    val pullPanelLayoutState = rememberPullPanelLayoutState()

    val isPanelExpanded = pullPanelLayoutState.isExpanded
    val fraction = pullPanelLayoutState.fraction

    val createRecordFileLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            viewModel.recordVideo(uri)
        }

    LifecycleResumeEffect(Unit) {
        with(helper) {
            isSystemBarUseDarkMode = true
            onPipModeChanged = OnPipModeChanged { info ->
                isPipMode = info.isInPictureInPictureMode
                if (!isPipMode) {
                    maskState.wake()
                    isAutoZappingMode = false
                }
            }
        }
        onPauseOrDispose {
            viewModel.closeDlnaDevices()
        }
    }

    LaunchedEffect(zappingMode, playerState.videoSize) {
        val videoSize = playerState.videoSize
        if (isAutoZappingMode && zappingMode && !isPipMode) {
            maskState.sleep()
            val rect = if (videoSize.isNotEmpty) videoSize
            else Rect(0, 0, 1920, 1080)
            helper.enterPipMode(rect)
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { brightness }
            .drop(1)
            .onEach { helper.brightness = it }
            .launchIn(this)

        snapshotFlow { maskState.visible }
            .onEach { visible ->
                helper.navigationBarVisibility = visible
                viewModel.onMaskStateChanged(visible)
            }
            .launchIn(this)
        snapshotFlow { pullPanelLayoutState.fraction }
            .drop(1)
            .onEach { maskState.sleep() }
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

    var dimension: MaskDimension by remember { mutableStateOf(MaskDimension()) }
    val onDimensionChanged = { size: MaskDimension -> dimension = size }
    val topPadding by animateDpAsState(dimension.top.takeOrElse { 0.dp }.takeIf { isPanelExpanded }
        ?: 0.dp)
    val bottomPadding by animateDpAsState(dimension.bottom.takeOrElse { 0.dp }
        .takeIf { isPanelExpanded } ?: 0.dp)

    val aspectRatio = with(density) {
        val source = playerState.videoSize
        val scaledSourceWidth = source.width()
        val scaledSourceHeight = source.height()
        val sourceAspectRatio = (scaledSourceWidth * 1f / scaledSourceHeight)
        if (sourceAspectRatio.isNaN()) {
            PullPanelLayoutDefaults.AspectRatio
        } else {
            val destWidth = windowInfo.containerSize.width.toDp()
            val destHeight = destWidth / sourceAspectRatio
            (destWidth * 1f / (destHeight + topPadding + bottomPadding))
        }
    }
    val onAlignment = { size: IntSize, space: IntSize ->
        val centerX = (space.width - size.width).toFloat() / 2f
        val centerY = (space.height - size.height).toFloat() / 2f
        val x = centerX
        val y = centerY - (centerY - with(density) { topPadding.toPx() }) * fraction
        IntOffset(x.fastRoundToInt(), y.fastRoundToInt())
    }

    PullPanelLayout(
        state = pullPanelLayoutState,
        enabled = isPanelEnabled,
        aspectRatio = aspectRatio,
        useVertical = useVertical,
        panel = {
            PlayerPanel(
                title = channel?.title.orEmpty(),
                playlistTitle = playlist?.title.orEmpty(),
                channelId = channel?.id ?: -1,
                isPanelExpanded = isPanelExpanded,
                isChannelsSupported = !isSeriesPlaylist,
                isProgrammeSupported = isProgrammeSupported,
                useVertical = useVertical,
                channels = channels,
                programmes = programmes,
                programmeRange = programmeRange,
                programmeReminderIds = programmeReminderIds,
                onRemindProgramme = {
                    requestIgnoreBatteryOptimizations.checkPermissionOrRationale {
                        viewModel.onRemindProgramme(it)
                    }
                },
                onCancelRemindProgramme = viewModel::onCancelRemindProgramme,
            )
        },
        content = {
            ChannelPlayer(
                isSeriesPlaylist = isSeriesPlaylist,
                openDlnaDevices = {
                    viewModel.openDlnaDevices()
                    pullPanelLayoutState.collapse()
                },
                openChooseFormat = {
                    choosing = true
                    pullPanelLayoutState.collapse()
                },
                openOrClosePanel = {
                    if (isPanelExpanded) {
                        pullPanelLayoutState.collapse()
                    } else {
                        pullPanelLayoutState.expand()
                    }
                },
                onFavorite = viewModel::onFavorite,
                maskState = maskState,
                playerState = playerState,
                playlist = playlist,
                adjacentChannels = adjacentChannels,
                channel = channel,
                hasTrack = tracks.isNotEmpty(),
                isPanelExpanded = isPanelExpanded,
                volume = volume,
                onVolume = viewModel::onVolume,
                brightness = brightness,
                onBrightness = { brightness = it },
                speed = speed,
                onSpeedUpdated = {
                    viewModel.onSpeedUpdated(it)
                    speed = it
                },
                cwPosition = viewModel.cwPosition,
                onResetPlayback = viewModel::onResetPlayback,
                onPreviousChannelClick = viewModel::getPreviousChannel,
                onNextChannelClick = viewModel::getNextChannel,
                onEnterPipMode = {
                    helper.enterPipMode(playerState.videoSize)
                    maskState.unlockAll()
                    pullPanelLayoutState.collapse()
                },
                onDimensionChanged = onDimensionChanged,
                onAlignment = onAlignment
            )
        },
        modifier = modifier
    )

    DlnaDevicesBottomSheet(
        maskState = maskState,
        searching = searching,
        isDevicesVisible = isDevicesVisible,
        devices = devices,
        connectDlnaDevice = { viewModel.connectDlnaDevice(it) },
        openInExternalPlayer = {
            val channelUrl = channel?.url ?: return@DlnaDevicesBottomSheet
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(channelUrl.toUri(), "video/*")
                }.let { Intent.createChooser(it, openInExternalPlayerString.title()) }
            )
        },
        onDismiss = { viewModel.closeDlnaDevices() }
    )

    FormatsBottomSheet(
        visible = choosing,
        formats = tracks,
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
}

@Composable
private fun ChannelPlayer(
    maskState: MaskState,
    playerState: PlayerState,
    playlist: Playlist?,
    channel: Channel?,
    adjacentChannels: AdjacentChannels?,
    isSeriesPlaylist: Boolean,
    hasTrack: Boolean,
    isPanelExpanded: Boolean,
    volume: Float,
    brightness: Float,
    speed: Float,
    cwPosition: Long,
    onResetPlayback: () -> Unit,
    onFavorite: () -> Unit,
    openDlnaDevices: () -> Unit,
    openChooseFormat: () -> Unit,
    openOrClosePanel: () -> Unit,
    onVolume: (Float) -> Unit,
    onBrightness: (Float) -> Unit,
    onPreviousChannelClick: () -> Unit,
    onNextChannelClick: () -> Unit,
    onEnterPipMode: () -> Unit,
    onSpeedUpdated: (Float) -> Unit,
    onDimensionChanged: (MaskDimension) -> Unit,
    onAlignment: (size: IntSize, space: IntSize) -> IntOffset,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current

    val title = channel?.title ?: "--"
    val cover = channel?.cover.orEmpty()
    val playlistTitle = playlist?.title ?: "--"
    val favourite = channel?.favourite ?: false
    var gesture: MaskGesture? by remember { mutableStateOf(null) }
    val currentBrightness by rememberUpdatedState(brightness)
    val currentVolume by rememberUpdatedState(volume)
    val currentSpeed by rememberUpdatedState(speed)

    val clipMode by preferenceOf(PreferencesKeys.CLIP_MODE)
    val brightnessGesture by preferenceOf(PreferencesKeys.BRIGHTNESS_GESTURE)
    val volumeGesture by preferenceOf(PreferencesKeys.VOLUME_GESTURE)

    val useVertical = with(windowInfo.containerSize) { width < height }

    LaunchedEffect(cwPosition) {
        if (cwPosition != -1L) {
            maskState.wake(6.seconds)
        }
    }
    Box(modifier) {
        val state = rememberPlayerState(
            player = playerState.player,
            clipMode = clipMode
        )
        var dimension: MaskDimension by remember { mutableStateOf(MaskDimension()) }
        val topPadding = with(density) { dimension.top.takeOrElse { 0.dp }.toPx() }
        Player(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .align { size: IntSize, space: IntSize, _ ->
                    onAlignment(size, space)
                }
        )
        VerticalGestureArea(
            percent = currentBrightness,
            time = 0.65f,
            onDragStart = {
                gesture = MaskGesture.BRIGHTNESS
                maskState.sleep()
            },
            onDragEnd = { gesture = null },
            onDrag = onBrightness,
            onClick = maskState::toggle,
            modifier = Modifier
                .fillMaxHeight(0.7f)
                .fillMaxWidth(0.18f)
                .align(Alignment.CenterStart),
            enabled = brightnessGesture
        )

        VerticalGestureArea(
            percent = currentVolume,
            time = 0.35f,
            onDragStart = {
                gesture = MaskGesture.VOLUME
                maskState.sleep()
            },
            onDragEnd = { gesture = null },
            onDrag = onVolume,
            onClick = maskState::toggle,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(0.7f)
                .fillMaxWidth(0.18f),
            enabled = volumeGesture
        )

        ChannelMask(
            adjacentChannels = adjacentChannels,
            title = title,
            playlistTitle = playlistTitle,
            playerState = playerState,
            volume = volume,
            brightness = brightness,
            maskState = maskState,
            favourite = favourite,
            isSeriesPlaylist = isSeriesPlaylist,
            useVertical = useVertical,
            hasTrack = hasTrack,
            cwPosition = cwPosition,
            onResetPlayback = onResetPlayback,
            isPanelExpanded = isPanelExpanded,
            onFavorite = onFavorite,
            openDlnaDevices = openDlnaDevices,
            openChooseFormat = openChooseFormat,
            openOrClosePanel = openOrClosePanel,
            onVolume = onVolume,
            onEnterPipMode = onEnterPipMode,
            onPreviousChannelClick = onPreviousChannelClick,
            onNextChannelClick = onNextChannelClick,
            onSpeedUpdated = onSpeedUpdated,
            onSpeedStart = { gesture = MaskGesture.SPEED },
            onSpeedEnd = { gesture = null },
            gesture = gesture,
            onDimensionChanged = {
                dimension = it
                onDimensionChanged(it)
            }
        )

        if (gesture != null) {
            MaskGestureValuePanel(
                value = when (gesture) {
                    MaskGesture.BRIGHTNESS -> "${currentBrightness.times(100).toInt()}%"
                    MaskGesture.VOLUME -> "${currentVolume.times(100).toInt()}"
                    MaskGesture.SPEED -> "${"%.1f".format(currentSpeed)}x"
                    else -> ""
                },
                icon = when (gesture) {
                    MaskGesture.BRIGHTNESS -> when {
                        brightness < 0.5f -> Icons.Rounded.DarkMode
                        else -> Icons.Rounded.LightMode
                    }

                    MaskGesture.VOLUME -> when {
                        volume == 0f -> Icons.AutoMirrored.Rounded.VolumeOff
                        volume < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
                        else -> Icons.AutoMirrored.Rounded.VolumeUp
                    }

                    else -> Icons.Rounded.Speed
                },
                modifier = Modifier.align(Alignment.Center)
            )
        }

        LaunchedEffect(playerState.playerError) {
            if (playerState.playerError != null) {
                maskState.wake()
            }
        }
    }
}