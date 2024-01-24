@file:Suppress("UsingMaterialAndMaterial3Libraries")

package com.m3u.features.playlist.impl

import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.lifecycle.compose.LifecycleStartEffect
import com.m3u.core.wrapper.Event
import com.m3u.data.database.model.Stream
import com.m3u.features.playlist.Channel
import com.m3u.features.playlist.components.DialogStatus
import com.m3u.features.playlist.components.PlaylistDialog
import com.m3u.features.playlist.components.PlaylistPager
import com.m3u.features.playlist.components.StreamGallery
import com.m3u.i18n.R.string
import com.m3u.material.components.Background
import com.m3u.material.components.TextField
import com.m3u.material.ktx.isAtTop
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.helper.Action
import com.m3u.ui.EventHandler
import com.m3u.ui.helper.LocalHelper
import com.m3u.ui.Sort
import com.m3u.ui.SortBottomSheet
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Composable
internal fun PlaylistScreenImpl(
    channels: ImmutableList<Channel>,
    zapping: Stream?,
    query: String,
    onQuery: (String) -> Unit,
    rowCount: Int,
    scrollUp: Event<Unit>,
    sorts: ImmutableList<Sort>,
    sort: Sort,
    onSort: (Sort) -> Unit,
    navigateToStream: () -> Unit,
    onRefresh: () -> Unit,
    onFavorite: (streamId: Int, target: Boolean) -> Unit,
    ban: (streamId: Int) -> Unit,
    onSavePicture: (streamId: Int) -> Unit,
    createShortcut: (streamId: Int) -> Unit,
    isAtTopState: MutableState<Boolean>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val helper = LocalHelper.current
    val spacing = LocalSpacing.current
    val configuration = LocalConfiguration.current
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
        targetValue = MaterialTheme.colorScheme.background,
        label = "background"
    )
    val currentContentColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.onBackground,
        label = "on-background"
    )
    val focusManager = LocalFocusManager.current

    val sheetState = rememberModalBottomSheetState()

    var dialogStatus: DialogStatus by remember { mutableStateOf(DialogStatus.Idle) }
    var isSortSheetVisible by rememberSaveable { mutableStateOf(false) }

    LifecycleStartEffect(Unit) {
        helper.actions = persistentListOf(
            Action(
                icon = Icons.AutoMirrored.Rounded.Sort,
                contentDescription = "sort",
                onClick = { isSortSheetVisible = true }
            ),
            Action(
                icon = Icons.Rounded.Refresh,
                contentDescription = "refresh",
                onClick = onRefresh
            )
        )
        onStopOrDispose { }
    }

    Background {
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
                        placeholder = stringResource(string.feat_playlist_query_placeholder).uppercase()
                    )
                }
            },
            frontLayerContent = {
                Background(
                    modifier = Modifier.fillMaxSize()
                ) {
                    PlaylistPager(channels) { streams ->
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
                        StreamGallery(
                            state = state,
                            rowCount = actualRowCount,
                            streams = streams,
                            zapping = zapping,
                            sort = sort,
                            play = { streamId ->
                                helper.play(streamId)
                                navigateToStream()
                            },
                            onMenu = { dialogStatus = DialogStatus.Selections(it) },
                            modifier = modifier,
                        )
                    }
                }
            },
            backLayerBackgroundColor = currentColor,
            backLayerContentColor = currentContentColor,
            frontLayerScrimColor = currentColor.copy(alpha = 0.45f),
            frontLayerBackgroundColor = Color.Transparent,
            modifier = Modifier
                .padding(contentPadding)
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
            ban = ban,
            onSavePicture = onSavePicture,
            createShortcut = createShortcut
        )
    }
}
