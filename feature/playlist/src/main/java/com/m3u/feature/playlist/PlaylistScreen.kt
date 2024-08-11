@file:Suppress("UsingMaterialAndMaterial3Libraries")

package com.m3u.feature.playlist

import android.Manifest
import android.content.Intent
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material3.Text
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.google.accompanist.permissions.rememberPermissionState
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
import com.m3u.feature.playlist.internal.SmartphonePlaylistScreenImpl
import com.m3u.feature.playlist.internal.TvPlaylistScreenImpl
import com.m3u.i18n.R.string
import com.m3u.material.ktx.checkPermissionOrRationale
import com.m3u.material.ktx.createScheme
import com.m3u.material.ktx.interceptVolumeEvent
import com.m3u.material.ktx.tv
import com.m3u.material.ktx.thenIf
import com.m3u.material.model.LocalSpacing
import com.m3u.material.model.asTvScheme
import com.m3u.ui.Destination
import com.m3u.ui.EpisodesBottomSheet
import com.m3u.ui.Sort
import com.m3u.ui.helper.Fob
import com.m3u.ui.helper.LocalHelper
import com.m3u.ui.helper.Metadata
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import androidx.tv.material3.MaterialTheme as TvMaterialTheme

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
    val colorScheme = TvMaterialTheme.colorScheme
    val lifecycleOwner = LocalLifecycleOwner.current

    val tv = tv()

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

    val writeExternalPermission = rememberPermissionState(Manifest.permission.WRITE_EXTERNAL_STORAGE)

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
        createTvRecommend = { id -> viewModel.createTvRecommend(helper.activityContext, id) },
        isVodPlaylist = isVodPlaylist,
        isSeriesPlaylist = isSeriesPlaylist,
        getProgrammeCurrently = { channelId -> viewModel.getProgrammeCurrently(channelId) },
        modifier = Modifier
            .fillMaxSize()
            .thenIf(!tv && preferences.godMode) {
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
    createTvRecommend: (channelId: Int) -> Unit,
    contentPadding: PaddingValues,
    isVodPlaylist: Boolean,
    isSeriesPlaylist: Boolean,
    getProgrammeCurrently: suspend (channelId: String) -> Programme?,
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

    val tv = tv()
    if (!tv) {
        SmartphonePlaylistScreenImpl(
            categoryWithChannels = categoryWithChannels,
            pinnedCategories = pinnedCategories,
            onPinOrUnpinCategory = onPinOrUnpinCategory,
            onHideCategory = onHideCategory,
            zapping = zapping,
            query = query,
            onQuery = onQuery,
            rowCount = rowCount,
            scrollUp = scrollUp,
            contentPadding = contentPadding,
            onPlayChannel = onPlayChannel,
            isAtTopState = isAtTopState,
            refreshing = refreshing,
            onRefresh = onRefresh,
            sorts = sorts,
            sort = sort,
            onSort = onSort,
            favourite = favourite,
            onHide = hide,
            onSaveCover = savePicture,
            onCreateShortcut = createShortcut,
            isVodOrSeriesPlaylist = isVodPlaylist || isSeriesPlaylist,
            getProgrammeCurrently = getProgrammeCurrently,
            modifier = modifier
        )
    } else {
        val preferences = hiltPreferences()
        TvMaterialTheme(
            colorScheme = remember(preferences.argb) {
                createScheme(preferences.argb, true).asTvScheme()
            }
        ) {
            TvPlaylistScreenImpl(
                title = title,
                categoryWithChannels = categoryWithChannels,
                query = query,
                onQuery = onQuery,
                onPlayChannel = onPlayChannel,
                onRefresh = onRefresh,
                sorts = sorts,
                sort = sort,
                onSort = onSort,
                favorite = favourite,
                hide = hide,
                savePicture = savePicture,
                createTvRecommend = createTvRecommend,
                isVodOrSeriesPlaylist = isVodPlaylist || isSeriesPlaylist,
                getProgrammeCurrently = getProgrammeCurrently,
                modifier = modifier
            )
        }
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
