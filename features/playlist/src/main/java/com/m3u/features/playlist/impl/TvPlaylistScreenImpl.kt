package com.m3u.features.playlist.impl

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import com.m3u.core.wrapper.Message
import com.m3u.features.playlist.Channel
import com.m3u.features.playlist.components.ImmersiveBackground
import com.m3u.features.playlist.components.PlaylistDrawer
import com.m3u.features.playlist.components.PlaylistDrawerDefaults
import com.m3u.features.playlist.components.TvStreamGallery
import com.m3u.material.ktx.Edge
import com.m3u.material.ktx.blurEdge
import com.m3u.ui.LocalHelper
import com.m3u.ui.Sort
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun TvPlaylistScreenImpl(
    title: String,
    message: Message,
    channels: ImmutableList<Channel>,
    query: String,
    onQuery: (String) -> Unit,
    sorts: ImmutableList<Sort>,
    sort: Sort,
    onFavorite: (streamId: Int, target: Boolean) -> Unit,
    ban: (streamId: Int) -> Unit,
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

    val maxBrowserHeight = when {
        noPictureMode -> 320.dp
        multiCatalogs -> 256.dp
        else -> 180.dp
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)

    var pressMixed: Int? by remember { mutableStateOf(null) }
    var focusMixed: Int? by remember { mutableStateOf(null) }

    val pressStream by remember(channels) {
        derivedStateOf {
            pressMixed?.let {
                val (i, j) = TvPlaylistScreenImplDefaults.separate(it)
                channels[i].streams[j]
            }
        }
    }

    val focusStream by remember(channels) {
        derivedStateOf {
            focusMixed?.let {
                val (i, j) = TvPlaylistScreenImplDefaults.separate(it)
                channels[i].streams[j]
            }
        }
    }

    PlaylistDrawer(
        drawerState = drawerState,
        items = PlaylistDrawerDefaults.rememberStreamItems(
            stream = pressStream,
            onFavorite = onFavorite,
            ban = ban,
            createShortcut = createShortcut,
            savePicture = savePicture
        )
    ) {
        ImmersiveList(
            modifier = modifier.fillMaxWidth(),
            background = { _, _ ->
                ImmersiveBackground(
                    title = title,
                    stream = focusStream,
                    message = message,
                    noPictureMode = noPictureMode,
                    maxBrowserHeight = maxBrowserHeight,
                    onRefresh = onRefresh,
                    openSearchDrawer = {},
                    openSortDrawer = {}
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
                    onLongClick = { _, i, j ->
                        pressMixed = TvPlaylistScreenImplDefaults.combine(i, j)
                        drawerState.setValue(DrawerValue.Open)
                    },
                    onFocus = { _, i, j ->
                        focusMixed = TvPlaylistScreenImplDefaults.combine(i, j)
                    },
                    modifier = Modifier
                        .then(
                            if (noPictureMode) Modifier
                            else Modifier.blurEdge(
                                color = MaterialTheme.colorScheme.background,
                                edge = Edge.Top
                            )
                        )
                )
            }
        )
    }
}

private object TvPlaylistScreenImplDefaults {
    fun combine(i: Int, j: Int): Int = i shl 16 or j
    fun separate(mixed: Int): Pair<Int, Int> {
        val i = mixed shr 16 and 0x7FFF
        val j = mixed shr 0 and 0x7FFF
        return i to j
    }
}
