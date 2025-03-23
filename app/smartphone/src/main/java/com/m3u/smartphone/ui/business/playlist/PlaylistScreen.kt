package com.m3u.smartphone.ui.business.playlist

import android.Manifest
import android.content.Intent
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.content.res.Configuration.UI_MODE_TYPE_APPLIANCE
import android.content.res.Configuration.UI_MODE_TYPE_CAR
import android.content.res.Configuration.UI_MODE_TYPE_DESK
import android.content.res.Configuration.UI_MODE_TYPE_NORMAL
import android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
import android.content.res.Configuration.UI_MODE_TYPE_VR_HEADSET
import android.content.res.Configuration.UI_MODE_TYPE_WATCH
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.google.accompanist.permissions.rememberPermissionState
import com.m3u.business.playlist.PlaylistViewModel
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.core.util.basic.title
import com.m3u.core.wrapper.Event
import com.m3u.core.wrapper.eventOf
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Programme
import com.m3u.data.database.model.isSeries
import com.m3u.data.database.model.isVod
import com.m3u.data.database.model.type
import com.m3u.data.service.MediaCommand
import com.m3u.core.wrapper.Sort
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.material.components.TextField
import com.m3u.smartphone.ui.material.ktx.checkPermissionOrRationale
import com.m3u.smartphone.ui.material.ktx.interceptVolumeEvent
import com.m3u.smartphone.ui.material.ktx.isAtTop
import com.m3u.smartphone.ui.material.ktx.only
import com.m3u.smartphone.ui.material.ktx.split
import com.m3u.core.foundation.ui.thenIf
import com.m3u.smartphone.ui.business.playlist.components.BackdropScaffold
import com.m3u.smartphone.ui.business.playlist.components.BackdropValue
import com.m3u.smartphone.ui.material.model.LocalHazeState
import com.m3u.smartphone.ui.material.model.LocalSpacing
import com.m3u.smartphone.ui.business.playlist.components.ChannelGallery
import com.m3u.smartphone.ui.business.playlist.components.PlaylistTabRow
import com.m3u.smartphone.ui.business.playlist.components.rememberBackdropScaffoldState
import com.m3u.smartphone.ui.material.components.Destination
import com.m3u.smartphone.ui.material.components.EpisodesBottomSheet
import com.m3u.smartphone.ui.material.components.EventHandler
import com.m3u.smartphone.ui.material.components.MediaSheet
import com.m3u.smartphone.ui.material.components.MediaSheetValue
import com.m3u.smartphone.ui.material.components.SortBottomSheet
import com.m3u.smartphone.ui.common.helper.Action
import com.m3u.smartphone.ui.common.helper.Fob
import com.m3u.smartphone.ui.common.helper.LocalHelper
import com.m3u.smartphone.ui.common.helper.Metadata
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun PlaylistRoute(
    navigateToChannel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistViewModel = hiltViewModel(),
    contentPadding: PaddingValues = PaddingValues()
) {
    val context = LocalContext.current
    val preferences = hiltPreferences()
    val helper = LocalHelper.current
    val coroutineScope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val lifecycleOwner = LocalLifecycleOwner.current

    val zapping by viewModel.zapping.collectAsStateWithLifecycle()
    val playlistUrl by viewModel.playlistUrl.collectAsStateWithLifecycle()
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()

    val channels by viewModel.channels.collectAsStateWithLifecycle(
        minActiveState = Lifecycle.State.RESUMED
    )

    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val pinnedCategories by viewModel.pinnedCategories.collectAsStateWithLifecycle()
    val refreshing by viewModel.subscribingOrRefreshing.collectAsStateWithLifecycle()
    val series by viewModel.series.collectAsStateWithLifecycle()

    val isSeriesPlaylist by remember { derivedStateOf { playlist?.isSeries ?: false } }
    val isVodPlaylist by remember { derivedStateOf { playlist?.isVod ?: false } }

    val sorts = Sort.entries
    val sort by viewModel.sort.collectAsStateWithLifecycle()

    val query by viewModel.query.collectAsStateWithLifecycle()
    val scrollUp by viewModel.scrollUp.collectAsStateWithLifecycle()

    val writeExternalPermission =
        rememberPermissionState(Manifest.permission.WRITE_EXTERNAL_STORAGE)

    val postNotificationPermission = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) null
    else rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)

    val title by remember { derivedStateOf { playlist?.title?.title().orEmpty() } }
    val subtitle by remember {
        derivedStateOf {
            val spans = buildMap {
                val typeWithSource = playlist.run {
                    when {
                        this == null || query.isNotEmpty() -> null
                        source == DataSource.Xtream -> "$source $type".uppercase()
                        else -> null
                    }
                }
                if (typeWithSource != null) {
                    put(typeWithSource, SpanStyle(color = colorScheme.secondary))
                }
                if (query.isNotEmpty()) {
                    put("\"$query\"", SpanStyle(color = colorScheme.primary))
                }
            }

            buildAnnotatedString {
                spans.entries.forEachIndexed { index, (text, span) ->
                    withStyle(span) { append(text) }
                    if (index != spans.entries.size - 1) {
                        append(" ")
                    }
                }
            }
        }
    }

    LifecycleResumeEffect(
        title,
        subtitle,
        colorScheme
    ) {
        Metadata.title = AnnotatedString(title)
        coroutineScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                delay(400.milliseconds)
                Metadata.subtitle = subtitle
            }
        }

        Metadata.color = colorScheme.secondaryContainer
        Metadata.contentColor = colorScheme.onSecondaryContainer
        onPauseOrDispose {
            Metadata.subtitle = AnnotatedString("")
        }
    }

    LaunchedEffect(preferences.autoRefreshChannels, playlistUrl) {
        if (playlistUrl.isNotEmpty() && preferences.autoRefreshChannels) {
            viewModel.refresh()
        }
    }

    BackHandler(query.isNotEmpty()) {
        viewModel.query.value = ""
    }

    PlaylistScreen(
        title = playlist?.title.orEmpty(),
        query = query,
        onQuery = { viewModel.query.value = it },
        rowCount = preferences.rowCount,
        zapping = zapping,
        categoryWithChannels = channels,
        pinnedCategories = pinnedCategories,
        onPinOrUnpinCategory = { viewModel.pinOrUnpinCategory(it) },
        onHideCategory = { viewModel.hideCategory(it) },
        scrollUp = scrollUp,
        sorts = sorts,
        sort = sort,
        onSort = { viewModel.sort(it) },
        onPlayChannel = { channel ->
            if (!isSeriesPlaylist) {
                coroutineScope.launch {
                    helper.play(MediaCommand.Common(channel.id))
                    navigateToChannel()
                }
            } else {
                viewModel.series.value = channel
            }
        },
        onScrollUp = { viewModel.scrollUp.value = eventOf(Unit) },
        refreshing = refreshing,
        onRefresh = {
            if (postNotificationPermission == null) {
                viewModel.refresh()
                return@PlaylistScreen
            }
            postNotificationPermission.checkPermissionOrRationale(
                showRationale = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .apply {
                            putExtra(
                                Settings.EXTRA_APP_PACKAGE,
                                helper.activityContext.packageName
                            )
                        }
                    helper.activityContext.startActivity(intent)
                },
                block = {
                    viewModel.refresh()
                }
            )
        },
        contentPadding = contentPadding,
        favourite = viewModel::favourite,
        hide = viewModel::hide,
        savePicture = { id ->
            writeExternalPermission.checkPermissionOrRationale {
                viewModel.savePicture(id)
            }
        },
        createShortcut = { id -> viewModel.createShortcut(context, id) },
        isVodPlaylist = isVodPlaylist,
        isSeriesPlaylist = isSeriesPlaylist,
        getProgrammeCurrently = { channelId -> viewModel.getProgrammeCurrently(channelId) },
        modifier = Modifier
            .fillMaxSize()
            .thenIf(preferences.godMode) {
                Modifier.interceptVolumeEvent { event ->
                    preferences.rowCount = when (event) {
                        KeyEvent.KEYCODE_VOLUME_UP ->
                            (preferences.rowCount - 1).coerceAtLeast(1)

                        KeyEvent.KEYCODE_VOLUME_DOWN ->
                            (preferences.rowCount + 1).coerceAtMost(2)

                        else -> return@interceptVolumeEvent
                    }
                }
            }
            .then(modifier)
    )

    if (isSeriesPlaylist) {
        EpisodesBottomSheet(
            series = series,
            episodes = episodes,
            onEpisodeClick = { episode ->
                coroutineScope.launch {
                    series?.let {
                        val input = MediaCommand.XtreamEpisode(
                            channelId = it.id,
                            episode = episode
                        )
                        helper.play(input)
                        navigateToChannel()
                    }
                }
            },
            onRefresh = { viewModel.seriesReplay.value += 1 },
            onDismissRequest = {
                viewModel.series.value = null
            }
        )
    }
}

@OptIn(InternalComposeApi::class)
@Composable
private fun PlaylistScreen(
    title: String,
    query: String,
    onQuery: (String) -> Unit,
    rowCount: Int,
    zapping: Channel?,
    categoryWithChannels: List<PlaylistViewModel.CategoryWithChannels>,
    pinnedCategories: List<String>,
    onPinOrUnpinCategory: (String) -> Unit,
    onHideCategory: (String) -> Unit,
    sorts: List<Sort>,
    sort: Sort,
    onSort: (Sort) -> Unit,
    scrollUp: Event<Unit>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onPlayChannel: (Channel) -> Unit,
    onScrollUp: () -> Unit,
    favourite: (channelId: Int) -> Unit,
    hide: (channelId: Int) -> Unit,
    savePicture: (channelId: Int) -> Unit,
    createShortcut: (channelId: Int) -> Unit,
    contentPadding: PaddingValues,
    isVodPlaylist: Boolean,
    isSeriesPlaylist: Boolean,
    getProgrammeCurrently: suspend (channelId: Int) -> Programme?,
    modifier: Modifier = Modifier
) {
    val currentOnScrollUp by rememberUpdatedState(onScrollUp)

    val isAtTopState = remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        snapshotFlow { isAtTopState.value }
            .distinctUntilChanged()
            .onEach { newValue ->
                Metadata.fob = if (newValue) null
                else {
                    Fob(
                        icon = Icons.Rounded.KeyboardDoubleArrowUp,
                        rootDestination = Destination.Root.Foryou,
                        iconTextId = string.feat_playlist_scroll_up,
                        onClick = currentOnScrollUp
                    )
                }
            }
            .launchIn(this)
    }

    DisposableEffect(Unit) {
        onDispose { Metadata.fob = null }
    }

    val spacing = LocalSpacing.current
    val configuration = LocalConfiguration.current
    val focusManager = LocalFocusManager.current

    val scaffoldState = rememberBackdropScaffoldState(
        initialValue = BackdropValue.Concealed
    )
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                return if (scaffoldState.isRevealed) available
                else Offset.Zero
            }
        }
    }
    val currentColor = MaterialTheme.colorScheme.background
    val currentContentColor = MaterialTheme.colorScheme.onBackground

    val sheetState = rememberModalBottomSheetState()

    var mediaSheetValue: MediaSheetValue.PlaylistScreen by remember { mutableStateOf(MediaSheetValue.PlaylistScreen()) }
    var isSortSheetVisible by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(refreshing) {
        Metadata.actions = buildList {
            Action(
                icon = Icons.AutoMirrored.Rounded.Sort,
                contentDescription = "sort",
                onClick = { isSortSheetVisible = true }
            ).also { add(it) }
            Action(
                icon = Icons.Rounded.Refresh,
                enabled = !refreshing,
                contentDescription = "refresh",
                onClick = onRefresh
            ).also { add(it) }
        }
        onPauseOrDispose {
            Metadata.actions = emptyList()
        }
    }

    val categories = remember(categoryWithChannels) { categoryWithChannels.map { it.category } }
    var category by remember(categories) { mutableStateOf(categories.firstOrNull().orEmpty()) }

    val (inner, outer) = contentPadding split WindowInsetsSides.Bottom

    BackdropScaffold(
        scaffoldState = scaffoldState,
        gesturesEnabled = isAtTopState.value,
        appBar = {},
        frontLayerShape = RectangleShape,
        peekHeight = 0.dp,
        backLayerContent = {
            val coroutineScope = rememberCoroutineScope()
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(scaffoldState.currentValue) {
                if (scaffoldState.isConcealed) {
                    focusManager.clearFocus()
                } else {
                    focusRequester.requestFocus()
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
                    placeholder = stringResource(string.feat_playlist_query_placeholder).uppercase(),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .heightIn(max = 48.dp)
                )
            }
        },
        frontLayerContent = {
            val state = rememberLazyStaggeredGridState()
            LaunchedEffect(Unit) {
                snapshotFlow { state.isAtTop }
                    .onEach { isAtTopState.value = it }
                    .launchIn(this)
            }
            EventHandler(scrollUp) {
                state.scrollToItem(0)
            }
            val orientation = configuration.orientation
            val actualRowCount = remember(orientation, rowCount) {
                when (orientation) {
                    ORIENTATION_LANDSCAPE -> rowCount + 2
                    ORIENTATION_PORTRAIT -> rowCount
                    else -> rowCount
                }
            }
            var isExpanded by remember(sort == Sort.MIXED) {
                mutableStateOf(false)
            }
            BackHandler(isExpanded) { isExpanded = false }

            val tabs = @Composable {
                PlaylistTabRow(
                    selectedCategory = category,
                    categories = categories,
                    isExpanded = isExpanded,
                    bottomContentPadding = contentPadding only WindowInsetsSides.Bottom,
                    onExpanded = { isExpanded = !isExpanded },
                    onCategoryChanged = { category = it },
                    pinnedCategories = pinnedCategories,
                    onPinOrUnpinCategory = onPinOrUnpinCategory,
                    onHideCategory = onHideCategory
                )
            }

            val gallery = @Composable {
                val channel = remember(categoryWithChannels, category) {
                    categoryWithChannels.find { it.category == category }
                }
                ChannelGallery(
                    state = state,
                    rowCount = actualRowCount,
                    categoryWithChannels = channel,
                    zapping = zapping,
                    recently = sort == Sort.RECENTLY,
                    isVodOrSeriesPlaylist = isVodPlaylist || isSeriesPlaylist,
                    onClick = onPlayChannel,
                    contentPadding = inner,
                    onLongClick = {
                        mediaSheetValue = MediaSheetValue.PlaylistScreen(it)
                    },
                    getProgrammeCurrently = getProgrammeCurrently,
                    modifier = Modifier.haze(
                        LocalHazeState.current,
                        HazeDefaults.style(MaterialTheme.colorScheme.surface)
                    )
                )
            }
            Column(
                Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                if (!isExpanded) {
                    AnimatedVisibility(
                        visible = categories.size > 1,
                        enter = fadeIn(animationSpec = tween(400))
                    ) {
                        tabs()
                    }
                    gallery()
                } else {
                    AnimatedVisibility(
                        visible = categories.size > 1,
                        enter = fadeIn(animationSpec = tween(400))
                    ) {
                        tabs()
                    }
                }
            }
        },
        backLayerBackgroundColor = Color.Transparent,
        backLayerContentColor = currentContentColor,
        frontLayerScrimColor = currentColor.copy(alpha = 0.45f),
        frontLayerBackgroundColor = Color.Transparent,
        modifier = modifier
            .padding(outer)
            .nestedScroll(
                connection = connection
            )
    )

    SortBottomSheet(
        visible = isSortSheetVisible,
        sort = sort,
        sorts = sorts,
        sheetState = sheetState,
        onChanged = onSort,
        onDismissRequest = { isSortSheetVisible = false }
    )

    MediaSheet(
        value = mediaSheetValue,
        onFavouriteChannel = { channel ->
            favourite(channel.id)
            mediaSheetValue = MediaSheetValue.PlaylistScreen()
        },
        onHideChannel = { channel ->
            hide(channel.id)
            mediaSheetValue = MediaSheetValue.PlaylistScreen()
        },
        onSaveChannelCover = { channel ->
            savePicture(channel.id)
            mediaSheetValue = MediaSheetValue.PlaylistScreen()
        },
        onCreateShortcut = { channel ->
            createShortcut(channel.id)
            mediaSheetValue = MediaSheetValue.PlaylistScreen()
        },
        onDismissRequest = { mediaSheetValue = MediaSheetValue.PlaylistScreen() }
    )
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
            UI_MODE_TYPE_TELEVISION -> "Tv"
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
