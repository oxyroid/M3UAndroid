package com.m3u.features.playlist.internal

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.ImmersiveList
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.rememberDrawerState
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.data.database.model.Stream
import com.m3u.features.playlist.Channel
import com.m3u.features.playlist.components.ImmersiveBackground
import com.m3u.features.playlist.components.PlaylistDrawer
import com.m3u.features.playlist.components.PlaylistDrawerDefaults
import com.m3u.features.playlist.components.TvStreamGallery
import com.m3u.material.ktx.Edge
import com.m3u.material.ktx.blurEdge
import com.m3u.material.model.LocalHazeState
import com.m3u.ui.Sort
import com.m3u.ui.helper.LocalHelper
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import kotlinx.collections.immutable.ImmutableList

@Composable
@InternalComposeApi
internal fun TvPlaylistScreenImpl(
    title: String,
    channels: ImmutableList<Channel>,
    query: String,
    onQuery: (String) -> Unit,
    sorts: ImmutableList<Sort>,
    sort: Sort,
    onFavorite: (streamId: Int, target: Boolean) -> Unit,
    hide: (streamId: Int) -> Unit,
    savePicture: (streamId: Int) -> Unit,
    createShortcut: (streamId: Int) -> Unit,
    navigateToStream: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val helper = LocalHelper.current
    val pref = LocalPref.current

    val multiCatalogs = channels.size > 1
    val noPictureMode = pref.noPictureMode
    val darkMode = if (pref.followSystemTheme) isSystemInDarkTheme()
    else pref.darkMode

    val maxBrowserHeight = when {
        noPictureMode -> 320.dp
        multiCatalogs -> 256.dp
        else -> 180.dp
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)

    var press: Stream? by remember { mutableStateOf(null) }
    var focus: Stream? by remember { mutableStateOf(null) }

    PlaylistDrawer(
        drawerState = drawerState,
        items = PlaylistDrawerDefaults.rememberStreamMenuItems(
            stream = press,
            onFavorite = onFavorite,
            hide = hide,
            createShortcut = createShortcut,
            savePicture = savePicture
        )
    ) {
        ImmersiveList(
            modifier = modifier.fillMaxWidth(),
            background = { _, _ ->
                ImmersiveBackground(
                    title = title,
                    stream = focus,
                    noPictureMode = noPictureMode,
                    maxBrowserHeight = maxBrowserHeight,
                    onRefresh = onRefresh,
                    openSearchDrawer = {},
                    openSortDrawer = {},
                    modifier = Modifier.haze(
                        LocalHazeState.current,
                        HazeDefaults.style(MaterialTheme.colorScheme.background)
                    )
                )
            },
            list = {
                TvStreamGallery(
                    channels = channels,
                    maxBrowserHeight = maxBrowserHeight,
                    noPictureMode = noPictureMode,
                    onClick = { stream, _, _ ->
                        helper.play(stream.url)
                        navigateToStream()
                    },
                    onLongClick = { stream, _, _ ->
                        press = stream
                        drawerState.setValue(DrawerValue.Open)
                    },
                    onFocus = { stream, _, _ ->
                        focus = stream
                    },
                    modifier = Modifier.then(
                        if (noPictureMode || !darkMode) Modifier
                        else Modifier
                            .hazeChild(LocalHazeState.current, style = HazeStyle(blurRadius = 4.dp))
                            .blurEdge(
                                color = MaterialTheme.colorScheme.background,
                                edge = Edge.Top
                            )
                    )
                )
            }
        )
    }
}

