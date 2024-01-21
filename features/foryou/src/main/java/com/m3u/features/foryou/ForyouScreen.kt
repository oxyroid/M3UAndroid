package com.m3u.features.foryou

import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.core.util.basic.title
import com.m3u.data.database.model.Playlist
import com.m3u.features.foryou.components.ForyouDialog
import com.m3u.features.foryou.components.OnRename
import com.m3u.features.foryou.components.OnUnsubscribe
import com.m3u.features.foryou.components.PlaylistGallery
import com.m3u.features.foryou.components.PlaylistGalleryPlaceholder
import com.m3u.features.foryou.components.recommend.Recommend
import com.m3u.features.foryou.components.recommend.RecommendGallery
import com.m3u.features.foryou.model.PlaylistDetail
import com.m3u.i18n.R
import com.m3u.material.components.Background
import com.m3u.material.ktx.interceptVolumeEvent
import com.m3u.material.ktx.isTelevision
import com.m3u.material.ktx.minus
import com.m3u.material.ktx.only
import com.m3u.material.ktx.thenIf
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.ConnectBottomSheet
import com.m3u.ui.EventHandler
import com.m3u.ui.ResumeEvent
import com.m3u.ui.helper.Action
import com.m3u.ui.helper.LocalHelper
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun ForyouRoute(
    navigateToPlaylist: (Playlist) -> Unit,
    navigateToStream: () -> Unit,
    navigateToRecommendPlaylist: (Playlist, String) -> Unit,
    navigateToSettingPlaylistManagement: () -> Unit,
    resume: ResumeEvent,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: ForyouViewModel = hiltViewModel()
) {
    val title = stringResource(R.string.ui_title_foryou)

    val helper = LocalHelper.current
    val pref = LocalPref.current

    val tv = isTelevision()

    val details by viewModel.details.collectAsStateWithLifecycle()
    val recommend by viewModel.recommend.collectAsStateWithLifecycle()

    var isConnectSheetVisible by remember { mutableStateOf(false) }

    EventHandler(resume, title) {
        helper.deep = 0
        helper.title = title.title()
        helper.actions = persistentListOf(
            Action(
                icon = Icons.Rounded.Link,
                contentDescription = "link",
                onClick = { isConnectSheetVisible = true }
            ),
            Action(
                icon = Icons.Rounded.Add,
                contentDescription = "add",
                onClick = navigateToSettingPlaylistManagement
            )
        )
    }

    Background {
        ForyouScreen(
            details = details,
            recommend = recommend,
            rowCount = pref.rowCount,
            contentPadding = contentPadding,
            navigateToPlaylist = navigateToPlaylist,
            navigateToStream = navigateToStream,
            navigateToRecommendPlaylist = navigateToRecommendPlaylist,
            navigateToSettingPlaylistManagement = navigateToSettingPlaylistManagement,
            unsubscribe = { viewModel.unsubscribe(it) },
            rename = { playlistUrl, target -> viewModel.rename(playlistUrl, target) },
            modifier = Modifier
                .fillMaxSize()
                .thenIf(!tv && pref.godMode) {
                    Modifier.interceptVolumeEvent { event ->
                        pref.rowCount = when (event) {
                            KeyEvent.KEYCODE_VOLUME_UP -> (pref.rowCount - 1).coerceAtLeast(1)
                            KeyEvent.KEYCODE_VOLUME_DOWN -> (pref.rowCount + 1).coerceAtMost(2)
                            else -> return@interceptVolumeEvent
                        }
                    }
                }
                .then(modifier)
        )
        var loading by remember { mutableStateOf(false) }
        var code by remember { mutableStateOf("") }
        ConnectBottomSheet(
            visible = isConnectSheetVisible,
            code = code,
            loading = loading,
            onCode = { code = it },
            sheetState = rememberModalBottomSheetState { !loading },
            onDismissRequest = { isConnectSheetVisible = false },
            onConnect = { loading = true }
        )
    }
}

@Composable
private fun ForyouScreen(
    rowCount: Int,
    details: ImmutableList<PlaylistDetail>,
    recommend: Recommend,
    contentPadding: PaddingValues,
    navigateToPlaylist: (Playlist) -> Unit,
    navigateToStream: () -> Unit,
    navigateToRecommendPlaylist: (Playlist, String) -> Unit,
    navigateToSettingPlaylistManagement: () -> Unit,
    unsubscribe: OnUnsubscribe,
    rename: OnRename,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val spacing = LocalSpacing.current

    val actualRowCount = remember(rowCount, configuration.orientation) {
        when (configuration.orientation) {
            ORIENTATION_PORTRAIT -> rowCount
            else -> rowCount + 2
        }
    }
    var dialog: ForyouDialog by remember { mutableStateOf(ForyouDialog.Idle) }
    Background(modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.small),
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            val showRecommend = recommend.isNotEmpty()
            val showPlaylist = details.isNotEmpty()
            if (showRecommend) {
                Column {
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .height(contentPadding.calculateTopPadding())
                    )
                    RecommendGallery(
                        recommend = recommend,
                        navigateToStream = navigateToStream,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (showPlaylist) {
                val actualContentPadding = if (!showRecommend) contentPadding
                else contentPadding - contentPadding.only(WindowInsetsSides.Top)
                PlaylistGallery(
                    rowCount = actualRowCount,
                    details = details,
                    navigateToPlaylist = navigateToPlaylist,
                    onMenu = { dialog = ForyouDialog.Selections(it) },
                    contentPadding = actualContentPadding,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize()) {
                    PlaylistGalleryPlaceholder(
                        navigateToSettingPlaylistManagement = navigateToSettingPlaylistManagement,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
        ForyouDialog(
            status = dialog,
            update = { dialog = it },
            unsubscribe = unsubscribe,
            rename = rename
        )
    }

    BackHandler(dialog != ForyouDialog.Idle) {
        dialog = ForyouDialog.Idle
    }
}
