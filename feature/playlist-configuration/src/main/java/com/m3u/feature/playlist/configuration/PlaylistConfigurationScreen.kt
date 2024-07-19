package com.m3u.feature.playlist.configuration

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntSize
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.rememberPermissionState
import com.m3u.core.util.basic.title
import com.m3u.core.wrapper.Resource
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.epgUrlsOrXtreamXmlUrl
import com.m3u.data.parser.xtream.XtreamInfo
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.feature.playlist.configuration.components.AutoSyncProgrammesButton
import com.m3u.feature.playlist.configuration.components.EpgManifestGallery
import com.m3u.feature.playlist.configuration.components.SyncProgrammesButton
import com.m3u.feature.playlist.configuration.components.XtreamPanel
import com.m3u.i18n.R.string
import com.m3u.material.components.Background
import com.m3u.material.components.Icon
import com.m3u.material.components.PlaceholderField
import com.m3u.material.ktx.checkPermissionOrRationale
import com.m3u.material.model.LocalHazeState
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.helper.LocalHelper
import com.m3u.ui.helper.Metadata
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import kotlinx.datetime.LocalDateTime

@Composable
internal fun PlaylistConfigurationRoute(
    modifier: Modifier = Modifier,
    viewModel: PlaylistConfigurationViewModel = hiltViewModel(),
    contentPadding: PaddingValues = PaddingValues()
) {
    val helper = LocalHelper.current

    val permissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        null
    }

    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val manifest by viewModel.manifest.collectAsStateWithLifecycle()
    val subscribingOrRefreshingWorkInfo by viewModel.subscribingOrRefreshingWorkInfo.collectAsStateWithLifecycle()
    val expired by viewModel.expired.collectAsStateWithLifecycle()
    val xtreamUserInfo by viewModel.xtreamUserInfo.collectAsStateWithLifecycle()

    LifecycleResumeEffect(playlist?.title) {
        Metadata.title = AnnotatedString(playlist?.title?.title().orEmpty())
        Metadata.color = Color.Unspecified
        Metadata.contentColor = Color.Unspecified
        onPauseOrDispose {
        }
    }

    playlist?.let {
        PlaylistConfigurationScreen(
            playlist = it,
            manifest = manifest,
            subscribingOrRefreshing = subscribingOrRefreshingWorkInfo != null,
            expired = expired,
            xtreamUserInfo = xtreamUserInfo,
            onUpdatePlaylistTitle = viewModel::onUpdatePlaylistTitle,
            onUpdatePlaylistUserAgent = viewModel::onUpdatePlaylistUserAgent,
            onUpdateEpgPlaylist = viewModel::onUpdateEpgPlaylist,
            onUpdatePlaylistAutoRefreshProgrammes = viewModel::onUpdatePlaylistAutoRefreshProgrammes,
            onSyncProgrammes = {
                if (permissionState == null) {
                    viewModel.onSyncProgrammes()
                    return@PlaylistConfigurationScreen
                }
                permissionState.checkPermissionOrRationale(
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
                        viewModel.onSyncProgrammes()
                    }
                )
            },
            onCancelSyncProgrammes = viewModel::onCancelSyncProgrammes,
            modifier = modifier,
            contentPadding = contentPadding
        )
    }
}

@Composable
private fun PlaylistConfigurationScreen(
    playlist: Playlist,
    manifest: EpgManifest,
    subscribingOrRefreshing: Boolean,
    expired: LocalDateTime?,
    xtreamUserInfo: Resource<XtreamInfo.UserInfo>,
    onUpdatePlaylistTitle: (String) -> Unit,
    onUpdatePlaylistUserAgent: (String?) -> Unit,
    onUpdateEpgPlaylist: (PlaylistRepository.UpdateEpgPlaylistUseCase) -> Unit,
    onUpdatePlaylistAutoRefreshProgrammes: () -> Unit,
    onSyncProgrammes: () -> Unit,
    onCancelSyncProgrammes: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val spacing = LocalSpacing.current

    var title: String by remember(playlist.title) { mutableStateOf(playlist.title) }
    var userAgent: String by remember(playlist.userAgent) { mutableStateOf(playlist.userAgent.orEmpty()) }

    val hasChanged by remember(playlist.title, playlist.userAgent) {
        derivedStateOf { title != playlist.title || userAgent != playlist.userAgent.orEmpty() }
    }
    Background(modifier) {
        Box {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(spacing.small),
                contentPadding = contentPadding,
                modifier = Modifier
                    .haze(
                        LocalHazeState.current,
                        HazeStyle(MaterialTheme.colorScheme.surface)
                    )
                    .fillMaxSize()
                    .padding(spacing.medium)
            ) {
                item {
                    PlaceholderField(
                        text = title,
                        placeholder = stringResource(string.feat_playlist_configuration_title).title(),
                        onValueChange = { title = it },
                    )
                }

                item {
                    PlaceholderField(
                        text = userAgent,
                        placeholder = stringResource(string.feat_playlist_configuration_user_agent).title(),
                        onValueChange = { userAgent = it }
                    )
                }

                item {
                    AnimatedVisibility(
                        visible = playlist.epgUrlsOrXtreamXmlUrl().isNotEmpty(),
                        enter = fadeIn() + expandIn(
                            expandFrom = Alignment.BottomCenter,
                            initialSize = { IntSize(it.width, 0) }
                        ),
                        exit = fadeOut() + shrinkOut(
                            shrinkTowards = Alignment.BottomCenter,
                            targetSize = { IntSize(it.width, 0) }
                        )
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(spacing.small)
                        ) {
                            SyncProgrammesButton(
                                subscribingOrRefreshing = subscribingOrRefreshing,
                                expired = expired,
                                onSyncProgrammes = onSyncProgrammes,
                                onCancelSyncProgrammes = onCancelSyncProgrammes
                            )
                            AutoSyncProgrammesButton(
                                checked = playlist.autoRefreshProgrammes,
                                onCheckedChange = onUpdatePlaylistAutoRefreshProgrammes
                            )
                        }
                    }
                }

                if (playlist.source == DataSource.M3U) {
                    EpgManifestGallery(
                        playlistUrl = playlist.url,
                        manifest = manifest,
                        onUpdateEpgPlaylist = onUpdateEpgPlaylist
                    )
                }

                if (playlist.source == DataSource.Xtream) {
                    item {
                        XtreamPanel(
                            info = xtreamUserInfo,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            val fabBottomPadding by animateDpAsState(
                targetValue = maxOf(
                    WindowInsets.ime.asPaddingValues().calculateBottomPadding(),
                    contentPadding.calculateBottomPadding()
                ),
                label = "apply changes bottom padding"
            )
            AnimatedVisibility(
                visible = hasChanged,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = fabBottomPadding)
            ) {
                FloatingActionButton(
                    onClick = {
                        if (title != playlist.title) onUpdatePlaylistTitle(title)
                        if (userAgent != playlist.userAgent) onUpdatePlaylistUserAgent(userAgent)
                    },
                    modifier = Modifier.padding(spacing.medium)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Save,
                        contentDescription = "apply changes"
                    )
                }
            }
        }
    }
}
