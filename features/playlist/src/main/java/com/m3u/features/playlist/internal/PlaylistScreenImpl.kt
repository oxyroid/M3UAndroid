@file:Suppress("UsingMaterialAndMaterial3Libraries")

package com.m3u.features.playlist.internal

import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
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
import androidx.paging.compose.LazyPagingItems
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.core.wrapper.Event
import com.m3u.data.database.model.Stream
import com.m3u.features.playlist.Category
import com.m3u.features.playlist.components.DialogStatus
import com.m3u.features.playlist.components.PlaylistDialog
import com.m3u.features.playlist.components.PlaylistTabRow
import com.m3u.features.playlist.components.StreamGallery
import com.m3u.i18n.R.string
import com.m3u.material.components.Background
import com.m3u.material.components.TextField
import com.m3u.material.ktx.isAtTop
import com.m3u.material.ktx.split
import com.m3u.material.model.LocalHazeState
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.EventHandler
import com.m3u.ui.Sort
import com.m3u.ui.SortBottomSheet
import com.m3u.ui.helper.Action
import com.m3u.ui.helper.LocalHelper
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.haze
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Composable
@InternalComposeApi
internal fun PlaylistScreenImpl(
    categories: ImmutableList<Category>,
    streamPaged: LazyPagingItems<Stream>,
    pinnedCategories: ImmutableList<String>,
    onPinOrUnpinCategory: (String) -> Unit,
    onHideCategory: (String) -> Unit,
    zapping: Stream?,
    query: String,
    onQuery: (String) -> Unit,
    rowCount: Int,
    scrollUp: Event<Unit>,
    sorts: ImmutableList<Sort>,
    sort: Sort,
    onSort: (Sort) -> Unit,
    onStream: (Stream) -> Unit,
    onRefresh: () -> Unit,
    onFavorite: (streamId: Int, target: Boolean) -> Unit,
    hide: (streamId: Int) -> Unit,
    onSavePicture: (streamId: Int) -> Unit,
    createShortcut: (streamId: Int) -> Unit,
    isAtTopState: MutableState<Boolean>,
    isVodOrSeriesPlaylist: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val helper = LocalHelper.current
    val spacing = LocalSpacing.current
    val configuration = LocalConfiguration.current
    val pref = LocalPref.current
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

    var dialogStatus: DialogStatus by remember { mutableStateOf(DialogStatus.Idle) }
    var isSortSheetVisible by rememberSaveable { mutableStateOf(false) }

    // FIXME: Pass pref.paging will make topbar tremble.
    LifecycleResumeEffect(Unit) {
        helper.actions = buildList {
            if (!pref.paging) {
                Action(
                    icon = Icons.AutoMirrored.Rounded.Sort,
                    contentDescription = "sort",
                    onClick = { isSortSheetVisible = true }
                ).also { add(it) }
            }
            Action(
                icon = Icons.Rounded.Refresh,
                contentDescription = "refresh",
                onClick = onRefresh
            ).also { add(it) }
        }.toPersistentList()
        onPauseOrDispose {
            helper.actions = persistentListOf()
        }
    }

    var currentPage by remember(categories.size) {
        mutableIntStateOf(
            if (categories.isEmpty()) -1
            else 0
        )
    }

    val (inner, outer) = contentPadding split WindowInsetsSides.Bottom

    Box {
        BackdropScaffold(
            scaffoldState = scaffoldState,
            appBar = {},
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
                        placeholder = stringResource(string.feat_playlist_query_placeholder).uppercase()
                    )
                }
            },
            frontLayerContent = {
                Background(
                    modifier = Modifier.fillMaxSize()
                ) {
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
                    Column {
                        PlaylistTabRow(
                            page = currentPage,
                            onPageChanged = { currentPage = it },
                            categories = categories,
                            pinnedCategories = pinnedCategories,
                            onPinOrUnpinCategory = onPinOrUnpinCategory,
                            onHideCategory = onHideCategory
                        )
                        if (pref.paging || currentPage != -1) {
                            StreamGallery(
                                state = state,
                                rowCount = actualRowCount,
                                streams = if (pref.paging) persistentListOf()
                                else categories[currentPage].streams,
                                streamPaged = streamPaged,
                                zapping = zapping,
                                recently = sort == Sort.RECENTLY,
                                isVodOrSeriesPlaylist = isVodOrSeriesPlaylist,
                                onClick = onStream,
                                contentPadding = inner,
                                onLongClick = {
                                    dialogStatus = DialogStatus.Selections(it)
                                },
                                modifier = modifier.haze(
                                    LocalHazeState.current,
                                    HazeDefaults.style(MaterialTheme.colorScheme.surface)
                                )
                            )
                        }
                    }
                }
            },
            backLayerBackgroundColor = Color.Transparent,
            backLayerContentColor = currentContentColor,
            frontLayerScrimColor = currentColor.copy(alpha = 0.45f),
            frontLayerBackgroundColor = Color.Transparent,
            modifier = Modifier
                .padding(outer)
                .nestedScroll(
                    connection = connection,
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

        PlaylistDialog(
            status = dialogStatus,
            onUpdate = { dialogStatus = it },
            onFavorite = onFavorite,
            hide = hide,
            onSavePicture = onSavePicture,
            createShortcut = createShortcut
        )
    }
}
