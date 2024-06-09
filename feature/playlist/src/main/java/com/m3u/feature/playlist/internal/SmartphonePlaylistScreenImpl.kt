@file:Suppress("UsingMaterialAndMaterial3Libraries")

package com.m3u.feature.playlist.internal

import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.BackdropScaffold
import androidx.compose.material.BackdropValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.rememberBackdropScaffoldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.m3u.core.wrapper.Event
import com.m3u.data.database.model.Programme
import com.m3u.data.database.model.Channel
import com.m3u.feature.playlist.PlaylistViewModel
import com.m3u.feature.playlist.components.PlaylistTabRow
import com.m3u.feature.playlist.components.SmartphoneChannelGallery
import com.m3u.i18n.R.string
import com.m3u.material.components.TextField
import com.m3u.material.ktx.isAtTop
import com.m3u.material.ktx.only
import com.m3u.material.ktx.split
import com.m3u.material.model.LocalHazeState
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.EventHandler
import com.m3u.ui.MediaSheet
import com.m3u.ui.MediaSheetValue
import com.m3u.ui.Sort
import com.m3u.ui.SortBottomSheet
import com.m3u.ui.helper.Action
import com.m3u.ui.helper.Metadata
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Composable
@InternalComposeApi
internal fun SmartphonePlaylistScreenImpl(
    categoryWithChannels: List<PlaylistViewModel.CategoryWithChannels>,
    pinnedCategories: List<String>,
    onPinOrUnpinCategory: (String) -> Unit,
    onHideCategory: (String) -> Unit,
    zapping: Channel?,
    query: String,
    onQuery: (String) -> Unit,
    rowCount: Int,
    scrollUp: Event<Unit>,
    sorts: List<Sort>,
    sort: Sort,
    onSort: (Sort) -> Unit,
    onPlayChannel: (Channel) -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    favourite: (channelId: Int) -> Unit,
    onHide: (channelId: Int) -> Unit,
    onSaveCover: (channelId: Int) -> Unit,
    onCreateShortcut: (channelId: Int) -> Unit,
    isAtTopState: MutableState<Boolean>,
    isVodOrSeriesPlaylist: Boolean,
    getProgrammeCurrently: suspend (channelId: String) -> Programme?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val spacing = LocalSpacing.current
    val configuration = LocalConfiguration.current
    val focusManager = LocalFocusManager.current

    val scaffoldState = rememberBackdropScaffoldState(BackdropValue.Concealed)
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
            var isExpanded by remember { mutableStateOf(false) }
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
                SmartphoneChannelGallery(
                    state = state,
                    rowCount = actualRowCount,
                    categoryWithChannels = channel,
                    zapping = zapping,
                    recently = sort == Sort.RECENTLY,
                    isVodOrSeriesPlaylist = isVodOrSeriesPlaylist,
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
            onHide(channel.id)
            mediaSheetValue = MediaSheetValue.PlaylistScreen()
        },
        onSaveChannelCover = { channel ->
            onSaveCover(channel.id)
            mediaSheetValue = MediaSheetValue.PlaylistScreen()
        },
        onCreateShortcut = { channel ->
            onCreateShortcut(channel.id)
            mediaSheetValue = MediaSheetValue.PlaylistScreen()
        },
        onDismissRequest = { mediaSheetValue = MediaSheetValue.PlaylistScreen() }
    )
}
