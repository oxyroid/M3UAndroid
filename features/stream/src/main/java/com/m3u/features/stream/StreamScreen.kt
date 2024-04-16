package com.m3u.features.stream

import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.core.unspecified.unspecifiable
import com.m3u.core.util.basic.isNotEmpty
import com.m3u.core.util.basic.title
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.Stream
import com.m3u.data.service.MediaCommand
import com.m3u.features.stream.components.CoverPlaceholder
import com.m3u.features.stream.components.DlnaDevicesBottomSheet
import com.m3u.features.stream.components.FormatsBottomSheet
import com.m3u.i18n.R.string
import com.m3u.material.components.Background
import com.m3u.material.components.PullPanelLayout
import com.m3u.material.components.PullPanelLayoutValue
import com.m3u.material.components.mask.MaskInterceptor
import com.m3u.material.components.mask.MaskState
import com.m3u.material.components.mask.rememberMaskState
import com.m3u.material.components.rememberPullPanelLayoutState
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.FontFamilies
import com.m3u.ui.Player
import com.m3u.ui.helper.LocalHelper
import com.m3u.ui.helper.OnPipModeChanged
import com.m3u.ui.rememberPlayerState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalSharedTransitionApi::class)
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
    val configuration = LocalConfiguration.current
    val spacing = LocalSpacing.current

    val playerState: PlayerState by viewModel.playerState.collectAsStateWithLifecycle()
    val stream by viewModel.stream.collectAsStateWithLifecycle()
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val connected by viewModel.connected.collectAsStateWithLifecycle()
    val isDevicesVisible by viewModel.isDevicesVisible.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()

    val formats by viewModel.formats.collectAsStateWithLifecycle()
    val selectedFormats by viewModel.selectedFormats.collectAsStateWithLifecycle()

    val volume by viewModel.volume.collectAsStateWithLifecycle()
    val isSeriesPlaylist by viewModel.isSeriesPlaylist.collectAsStateWithLifecycle()

    val neighboring = viewModel.neighboring.collectAsLazyPagingItems()

    var brightness by rememberSaveable { mutableFloatStateOf(helper.brightness) }
    var isPipMode by rememberSaveable { mutableStateOf(false) }
    var isAutoZappingMode by rememberSaveable { mutableStateOf(true) }
    var choosing by rememberSaveable { mutableStateOf(false) }

    val isPanelSupported = configuration.screenWidthDp < configuration.screenHeightDp
    val isEpgPreferenceEnabled = pref.epg

    val maskState = rememberMaskState()
    val pullPanelLayoutState = rememberPullPanelLayoutState()

    val isPanelShowing = pullPanelLayoutState.value == PullPanelLayoutValue.EXPANDED

    LifecycleResumeEffect(Unit) {
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
            viewModel.closeDlnaDevices()
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
        SharedTransitionLayout {
            PullPanelLayout(
                state = pullPanelLayoutState,
                enabled = isPanelSupported,
                panel = {
                    Background {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = spacing.medium)
                        ) {
                            AnimatedVisibility(
                                visible = isPanelShowing,
                                modifier = Modifier.padding(horizontal = spacing.medium)
                            ) {
                                Text(
                                    text = stream?.title.orEmpty().trim(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .basicMarquee()
                                        .sharedElement(
                                            state = rememberSharedContentState("stream-title"),
                                            this
                                        )
                                )
                            }
                            AnimatedVisibility(
                                visible = isPanelShowing,
                                modifier = Modifier.padding(horizontal = spacing.medium)
                            ) {
                                Text(
                                    text = playlist?.title.orEmpty().trim().uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    color = LocalContentColor.current.copy(0.54f),
                                    fontFamily = FontFamilies.LexendExa,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .basicMarquee()
                                        .sharedElement(
                                            state = rememberSharedContentState("playlist-title"),
                                            this
                                        )
                                )
                            }

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                                contentPadding = PaddingValues(spacing.medium)
                            ) {
                                items(neighboring.itemCount) { i ->
                                    neighboring[i]?.let { currentStream ->
                                        val playing = currentStream.id == stream?.id
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (!playing) MaterialTheme.colorScheme.surface
                                                else MaterialTheme.colorScheme.onSurface,
                                                contentColor = if (!playing) MaterialTheme.colorScheme.onSurface
                                                else MaterialTheme.colorScheme.surface
                                            ),
                                            shape = AbsoluteRoundedCornerShape(spacing.medium),
                                            elevation = CardDefaults.cardElevation(
                                                if (playing) spacing.none else spacing.small
                                            ),
                                            onClick = {
                                                coroutineScope.launch {
                                                    helper.play(
                                                        MediaCommand.Live(currentStream.id)
                                                    )
                                                }
                                            }
                                        ) {
                                            Text(
                                                text = currentStream.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold.takeIf { playing },
                                                modifier = Modifier.padding(spacing.medium)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                content = {
                    StreamPlayer(
                        isSeriesPlaylist = isSeriesPlaylist,
                        openDlnaDevices = {
                            viewModel.openDlnaDevices()
                            pullPanelLayoutState.collapse()
                        },
                        openChooseFormat = {
                            choosing = true
                            pullPanelLayoutState.collapse()
                        },
                        onFavourite = viewModel::onFavourite,
                        onBackPressed = onBackPressed,
                        maskState = maskState,
                        playerState = playerState,
                        playlist = playlist,
                        stream = stream,
                        formatsIsNotEmpty = formats.isNotEmpty(),
                        isPanelShowing = isPanelShowing,
                        brightness = brightness,
                        volume = volume,
                        onBrightness = { brightness = it },
                        onVolume = viewModel::onVolume,
                        modifier = modifier
                    )
                },
                onValueChanged = { state ->
                    when (state) {
                        PullPanelLayoutValue.EXPANDED -> {
                            maskState.lock(PullPanelLayoutValue.EXPANDED)
                        }

                        PullPanelLayoutValue.COLLAPSED -> {
                            maskState.unlock(PullPanelLayoutValue.EXPANDED, 2400.milliseconds)
                        }
                    }
                }
            )
        }

        DlnaDevicesBottomSheet(
            maskState = maskState,
            searching = searching,
            isDevicesVisible = isDevicesVisible,
            devices = devices,
            connected = connected,
            connectDlnaDevice = { viewModel.connectDlnaDevice(it) },
            disconnectDlnaDevice = { viewModel.disconnectDlnaDevice(it) },
            openInExternalPlayer = {
                val streamUrl = stream?.url ?: return@DlnaDevicesBottomSheet
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(streamUrl), "video/*")
                    }.let { Intent.createChooser(it, openInExternalPlayerString.title()) }
                )
            },
            onDismiss = { viewModel.closeDlnaDevices() }
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
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.StreamPlayer(
    maskState: MaskState,
    playerState: PlayerState,
    playlist: Playlist?,
    stream: Stream?,
    isSeriesPlaylist: Boolean,
    formatsIsNotEmpty: Boolean,
    isPanelShowing: Boolean,
    volume: Float,
    brightness: Float,
    onFavourite: () -> Unit,
    onBackPressed: () -> Unit,
    openDlnaDevices: () -> Unit,
    openChooseFormat: () -> Unit,
    onVolume: (Float) -> Unit,
    onBrightness: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stream?.title ?: "--"
    val cover = stream?.cover.orEmpty()
    val playlistTitle = playlist?.title ?: "--"
    val favourite = stream?.favourite ?: false

    val pref = LocalPref.current

    Background(
        color = Color.Black,
        contentColor = Color.White,
    ) {
        Box(modifier) {
            val state = rememberPlayerState(
                player = playerState.player,
                clipMode = pref.clipMode
            )

            Player(
                state = state,
                modifier = Modifier.fillMaxSize()
            )

            val shouldShowPlaceholder =
                !pref.noPictureMode && cover.isNotEmpty() && playerState.videoSize.isEmpty

            CoverPlaceholder(
                visible = shouldShowPlaceholder,
                cover = cover,
                modifier = Modifier.align(Alignment.Center)
            )

            PlayerMaskImpl(
                cover = cover,
                title = title,
                playlistTitle = playlistTitle,
                playerState = playerState,
                volume = volume,
                brightness = brightness,
                maskState = maskState,
                favourite = favourite,
                isSeriesPlaylist = isSeriesPlaylist,
                formatsIsNotEmpty = formatsIsNotEmpty,
                isPanelShowing = isPanelShowing,
                onFavourite = onFavourite,
                onBackPressed = onBackPressed,
                openDlnaDevices = openDlnaDevices,
                openChooseFormat = openChooseFormat,
                onVolume = onVolume,
                onBrightness = onBrightness
            )

            LaunchedEffect(playerState.playerError) {
                if (playerState.playerError != null) {
                    maskState.wake()
                }
            }
        }
    }
}
