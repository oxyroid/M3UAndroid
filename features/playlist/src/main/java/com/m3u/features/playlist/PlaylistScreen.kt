@file:Suppress("UsingMaterialAndMaterial3Libraries")

package com.m3u.features.playlist

import android.Manifest
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.content.res.Configuration.UI_MODE_TYPE_APPLIANCE
import android.content.res.Configuration.UI_MODE_TYPE_CAR
import android.content.res.Configuration.UI_MODE_TYPE_DESK
import android.content.res.Configuration.UI_MODE_TYPE_MASK
import android.content.res.Configuration.UI_MODE_TYPE_NORMAL
import android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
import android.content.res.Configuration.UI_MODE_TYPE_VR_HEADSET
import android.content.res.Configuration.UI_MODE_TYPE_WATCH
import android.os.Build
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.BackdropScaffold
import androidx.compose.material.BackdropValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.rememberBackdropScaffoldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.lazy.grid.rememberTvLazyGridState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.core.util.compose.observableStateOf
import com.m3u.core.wrapper.Event
import com.m3u.data.database.entity.Stream
import com.m3u.features.playlist.components.DialogStatus
import com.m3u.features.playlist.components.PlaylistDialog
import com.m3u.features.playlist.components.PlaylistPager
import com.m3u.features.playlist.components.StreamGallery
import com.m3u.features.playlist.components.TvStreamGallery
import com.m3u.i18n.R.string
import com.m3u.material.components.Background
import com.m3u.material.components.TextField
import com.m3u.material.ktx.interceptVolumeEvent
import com.m3u.material.ktx.isAtTop
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.Action
import com.m3u.ui.Destination
import com.m3u.ui.EventHandler
import com.m3u.ui.Fob
import com.m3u.ui.LocalHelper
import com.m3u.ui.MessageEventHandler
import com.m3u.ui.SortBottomSheet
import com.m3u.ui.isAtTop
import com.m3u.ui.repeatOnLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch

@Composable
internal fun PlaylistRoute(
    contentPadding: PaddingValues,
    playlistUrl: String,
    recommend: String?,
    navigateToStream: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val helper = LocalHelper.current
    val pref = LocalPref.current

    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val zapping by viewModel.zapping.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val sorts = viewModel.sorts
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()

    val sheetState = rememberModalBottomSheetState()

    // If you try to check or request the WRITE_EXTERNAL_STORAGE on Android 13+,
    // it will always return false.
    // So you'll have to skip the permission check/request completely on Android 13+.
    val writeExternalPermissionRequired = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

    val writeExternalPermissionState = rememberPermissionState(
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    var dialogStatus: DialogStatus by remember { mutableStateOf(DialogStatus.Idle) }
    var isSortSheetVisible by rememberSaveable { mutableStateOf(false) }

    MessageEventHandler(message)

    LaunchedEffect(playlistUrl, recommend) {
        viewModel.onEvent(PlaylistEvent.Init(playlistUrl, recommend))
    }

    helper.repeatOnLifecycle {
        actions = persistentListOf(
            Action(
                icon = Icons.AutoMirrored.Rounded.Sort,
                contentDescription = "sort",
                onClick = { isSortSheetVisible = true }
            ),
            Action(
                icon = Icons.Rounded.Refresh,
                contentDescription = "refresh",
                onClick = { viewModel.onEvent(PlaylistEvent.Refresh) }
            )
        )
    }

    LaunchedEffect(pref.autoRefresh, playlistUrl) {
        if (playlistUrl.isNotEmpty() && pref.autoRefresh) {
            viewModel.onEvent(PlaylistEvent.Refresh)
        }
    }

    BackHandler(query.isNotEmpty()) {
        viewModel.onEvent(PlaylistEvent.Query(""))
    }

    BackHandler(refreshing) {}

    val interceptVolumeEventModifier = remember(pref.godMode) {
        if (pref.godMode) {
            Modifier.interceptVolumeEvent { event ->
                pref.rowCount = when (event) {
                    KeyEvent.KEYCODE_VOLUME_UP -> (pref.rowCount - 1).coerceAtLeast(1)
                    KeyEvent.KEYCODE_VOLUME_DOWN -> (pref.rowCount + 1).coerceAtMost(3)
                    else -> return@interceptVolumeEvent
                }
            }
        } else Modifier
    }
    Background {
        SortBottomSheet(
            visible = isSortSheetVisible,
            sort = sort,
            sorts = sorts,
            sheetState = sheetState,
            onChanged = { viewModel.sort(it) },
            onDismissRequest = { isSortSheetVisible = false }
        )
        PlaylistScreen(
            query = query,
            onQuery = { viewModel.onEvent(PlaylistEvent.Query(it)) },
            rowCount = pref.rowCount,
            zapping = zapping,
            channels = channels,
            scrollUp = state.scrollUp,
            navigateToStream = navigateToStream,
            onMenu = {
                dialogStatus = DialogStatus.Selections(it)
            },
            onScrollUp = { viewModel.onEvent(PlaylistEvent.ScrollUp) },
            contentPadding = contentPadding,
            modifier = modifier
                .fillMaxSize()
                .then(interceptVolumeEventModifier)
        )

        PlaylistDialog(
            status = dialogStatus,
            onUpdate = { dialogStatus = it },
            onFavorite = { id, target -> viewModel.onEvent(PlaylistEvent.Favourite(id, target)) },
            ban = { id, target -> viewModel.onEvent(PlaylistEvent.Ban(id, target)) },
            onSavePicture = { id ->
                if (writeExternalPermissionRequired && writeExternalPermissionState.status is PermissionStatus.Denied) {
                    writeExternalPermissionState.launchPermissionRequest()
                    return@PlaylistDialog
                }
                viewModel.onEvent(PlaylistEvent.SavePicture(id))
            },
            createShortcut = { id ->
                viewModel.onEvent(PlaylistEvent.CreateShortcut(context, id))
            }
        )
    }
}

@Composable
private fun PlaylistScreen(
    query: String,
    onQuery: (String) -> Unit,
    rowCount: Int,
    zapping: Stream?,
    channels: ImmutableList<Channel>,
    scrollUp: Event<Unit>,
    navigateToStream: () -> Unit,
    onMenu: (Stream) -> Unit,
    onScrollUp: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val helper = LocalHelper.current
    val theme = MaterialTheme.colorScheme
    val pref = LocalPref.current
    val configuration = LocalConfiguration.current
    val spacing = LocalSpacing.current
    Box {
        val isAtTopState = remember {
            observableStateOf(true) { newValue ->
                helper.fob = if (newValue) null
                else {
                    Fob(
                        icon = Icons.Rounded.KeyboardDoubleArrowUp,
                        rootDestination = Destination.Root.Foryou,
                        iconTextId = string.feat_playlist_scroll_up,
                        onClick = onScrollUp
                    )
                }
            }
        }

        val scaffoldState = rememberBackdropScaffoldState(BackdropValue.Concealed)
        val connection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    return if (scaffoldState.isRevealed) available
                    else Offset.Zero
                }
            }
        }
        val currentColor by animateColorAsState(
            targetValue = theme.background,
            label = "background"
        )
        val currentContentColor by animateColorAsState(
            targetValue = theme.onBackground,
            label = "on-background"
        )
        val focusManager = LocalFocusManager.current

        BackdropScaffold(
            scaffoldState = scaffoldState,
            appBar = { /*TODO*/ },
            frontLayerShape = RectangleShape,
            peekHeight = 0.dp,
            backLayerContent = {
                val coroutineScope = rememberCoroutineScope()
                LaunchedEffect(scaffoldState.currentValue) {
                    if (scaffoldState.isConcealed) {
                        focusManager.clearFocus()
                    }
                }
                BackHandler(scaffoldState.isRevealed || query.isNotEmpty()) {
                    if (scaffoldState.isRevealed) {
                        coroutineScope.launch {
                            scaffoldState.conceal()
                        }
                    }
                    if (query.isNotEmpty()) {
                        onQuery("")
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(spacing.medium)
                        .fillMaxWidth()
                ) {
                    TextField(
                        text = query,
                        onValueChange = onQuery,
                        fontWeight = FontWeight.Bold,
                        placeholder = stringResource(string.feat_playlist_query_placeholder).capitalize(
                            Locale.current
                        )
                    )
                }
            },
            frontLayerContent = {
                Background(
                    modifier = Modifier.fillMaxSize()
                ) {
                    PlaylistPager(channels, zapping) { streams, zapping ->
                        val type = configuration.uiMode and UI_MODE_TYPE_MASK
                        when {
                            !pref.useCommonUIMode && type == UI_MODE_TYPE_TELEVISION -> {
                                val state = rememberTvLazyGridState()
                                LaunchedEffect(state.isAtTop) {
                                    isAtTopState.value = state.isAtTop
                                }
                                EventHandler(scrollUp) {
                                    state.animateScrollToItem(0)
                                }
                                TvStreamGallery(
                                    state = state,
                                    rowCount = 4,
                                    streams = streams,
                                    zapping = zapping,
                                    play = { url ->
                                        helper.play(url)
                                        navigateToStream()
                                    },
                                    onMenu = onMenu,
                                    modifier = modifier
                                )
                            }

                            else -> {
                                val state = rememberLazyStaggeredGridState()
                                LaunchedEffect(state.isAtTop) {
                                    isAtTopState.value = state.isAtTop
                                }
                                EventHandler(scrollUp) {
                                    state.animateScrollToItem(0)
                                }
                                val orientation = configuration.orientation
                                val actualRowCount = remember(orientation, rowCount) {
                                    when (orientation) {
                                        ORIENTATION_LANDSCAPE -> rowCount + 2
                                        ORIENTATION_PORTRAIT -> rowCount
                                        else -> rowCount
                                    }
                                }
                                StreamGallery(
                                    state = state,
                                    rowCount = actualRowCount,
                                    streams = streams,
                                    zapping = zapping,
                                    play = { url ->
                                        helper.play(url)
                                        navigateToStream()
                                    },
                                    onMenu = onMenu,
                                    modifier = modifier,
                                )
                            }
                        }
                    }
                }
            },
            backLayerBackgroundColor = currentColor,
            backLayerContentColor = currentContentColor,
            frontLayerScrimColor = currentColor.copy(alpha = 0.45f),
            frontLayerBackgroundColor = Color.Transparent,
            modifier = Modifier
                .padding(top = contentPadding.calculateTopPadding())
                .nestedScroll(
                    connection = connection,
                )
        )
    }
}

@Composable
@Suppress("UNUSED")
private fun UnsupportedUIModeContent(
    type: Int,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val spacing = LocalSpacing.current

    val device = remember(type) {
        when (type) {
            UI_MODE_TYPE_NORMAL -> "Normal"
            UI_MODE_TYPE_DESK -> "Desktop"
            UI_MODE_TYPE_CAR -> "Car"
            UI_MODE_TYPE_TELEVISION -> "Television"
            UI_MODE_TYPE_APPLIANCE -> "Appliance"
            UI_MODE_TYPE_WATCH -> "Watch"
            UI_MODE_TYPE_VR_HEADSET -> "VR-Headset"
            else -> "Device Type: $type"
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            spacing.medium,
            Alignment.CenterVertically
        ),
        modifier = modifier.fillMaxSize()
    ) {
        Text("Unsupported UI Mode: $device")
        if (description != null) {
            Text(description)
        }
    }
}
