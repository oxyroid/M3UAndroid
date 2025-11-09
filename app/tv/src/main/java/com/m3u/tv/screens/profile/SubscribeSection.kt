package com.m3u.tv.screens.profile

import android.view.KeyEvent.KEYCODE_DPAD_UP
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.business.playlist.PlaylistViewModel
import com.m3u.business.setting.SettingViewModel
import com.m3u.core.foundation.ui.thenIf
import com.m3u.data.database.model.DataSource
import com.m3u.i18n.R
import com.m3u.tv.screens.dashboard.DashboardTopBarItemIndicator
import com.m3u.tv.screens.dashboard.rememberChildPadding
import com.m3u.tv.theme.JetStreamCardShape
import com.m3u.tv.ui.component.TextField
import com.m3u.tv.utils.createInitialFocusRestorerModifiers
import com.m3u.tv.utils.occupyScreenSize
import kotlinx.coroutines.launch

@Immutable
data class AccountsSectionData(
    val title: String,
    val value: String? = null,
    val onClick: () -> Unit = {}
)

@Composable
fun SettingViewModel.SubscribeSection() {
    val childPadding = rememberChildPadding()
    val playlistViewModel: PlaylistViewModel = hiltViewModel()
    val dataSources = listOf(
        DataSource.WebDrop,
        DataSource.Xtream,
        DataSource.M3U,
        DataSource.EPG
    )

    // Track if "Manage" tab is selected
    var isManageTabSelected by remember { mutableStateOf(false) }

    val focusRequesters = remember { List(size = dataSources.size + 2) { FocusRequester() } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = childPadding.start)
    ) {
        item {
            val (parent, child) = createInitialFocusRestorerModifiers()
            val tabIndex =
                remember(properties.selectedState.value, isManageTabSelected) {
                    if (isManageTabSelected) dataSources.size
                    else dataSources.indexOf(properties.selectedState.value)
                }
            var isTabRowFocused by remember { mutableStateOf(false) }
            TabRow(
                selectedTabIndex = tabIndex,
                indicator = { tabPositions, _ ->
                    DashboardTopBarItemIndicator(
                        currentTabPosition = tabPositions[tabIndex],
                        anyTabFocused = isTabRowFocused,
                        shape = JetStreamCardShape
                    )
                },
                separator = { Spacer(modifier = Modifier) },
                modifier = Modifier
                    .onFocusChanged {
                        isTabRowFocused = it.isFocused || it.hasFocus
                    }
                    .onPreviewKeyEvent {
                        if (it.nativeKeyEvent.keyCode == KEYCODE_DPAD_UP) {
                            return@onPreviewKeyEvent true
                        }
                        false
                    }
                    .then(parent)
            ) {
                dataSources.forEachIndexed { index, dataSource ->
                    val isSelected = dataSource == properties.selectedState.value && !isManageTabSelected
                    Tab(
                        selected = isSelected,
                        onFocus = {
                            properties.selectedState.value = dataSource
                            isManageTabSelected = false
                        },
                        modifier = Modifier
                            .height(32.dp)
                            .focusRequester(focusRequesters[index + 1])
                            .thenIf(isSelected) { child }
                    ) {
                        Text(
                            text = stringResource(dataSource.resId),
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = LocalContentColor.current
                            ),
                            modifier = Modifier
                                .occupyScreenSize()
                                .padding(horizontal = 16.dp)
                        )
                    }
                }

                // Manage Playlists tab
                Tab(
                    selected = isManageTabSelected,
                    onFocus = { isManageTabSelected = true },
                    modifier = Modifier
                        .height(32.dp)
                        .focusRequester(focusRequesters[dataSources.size + 1])
                        .thenIf(isManageTabSelected) { child }
                ) {
                    Text(
                        text = "MANAGE PLAYLISTS",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = LocalContentColor.current
                        ),
                        modifier = Modifier
                            .occupyScreenSize()
                            .padding(horizontal = 16.dp)
                    )
                }
            }
        }

        if (isManageTabSelected) {
            managePlaylistsPageConfiguration(this, playlistViewModel)
        } else {
            when (properties.selectedState.value) {
                DataSource.M3U -> m3uPageConfiguration(this)
                DataSource.EPG -> epgPageConfiguration(this, playlistViewModel)
                DataSource.Xtream -> xtreamPageConfiguration(this)
                DataSource.WebDrop -> webDropPageConfiguration(this, playlistViewModel)
                else -> {}
            }
        }
    }
}

private fun SettingViewModel.m3uPageConfiguration(
    scope: LazyListScope
) {
    with(scope) {
        input(
            value = properties.titleState.value,
            onValueChanged = { properties.titleState.value = it },
            placeholder = R.string.feat_setting_placeholder_title
        )
        input(
            value = properties.urlState.value,
            onValueChanged = { properties.urlState.value = it },
            placeholder = R.string.feat_setting_placeholder_url
        )
        item {
            Button(
                onClick = this@m3uPageConfiguration::subscribe,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.feat_setting_label_subscribe).uppercase(),
                )
            }
        }
    }
}

private fun SettingViewModel.epgPageConfiguration(
    scope: LazyListScope,
    playlistViewModel: PlaylistViewModel
) {
    with(scope) {
        // Set default test values for EPG if fields are empty
        item {
            LaunchedEffect(Unit) {
                if (properties.titleState.value.isEmpty()) {
                    properties.titleState.value = "sweden"
                }
                if (properties.epgState.value.isEmpty()) {
                    properties.epgState.value = "https://raw.githubusercontent.com/globetvapp/epg/refs/heads/main/Sweden/sweden1.xml"
                }
            }
        }
        input(
            value = properties.titleState.value,
            onValueChanged = { properties.titleState.value = it },
            placeholder = R.string.feat_setting_placeholder_epg_title
        )
        input(
            value = properties.epgState.value,
            onValueChanged = { properties.epgState.value = it },
            placeholder = R.string.feat_setting_placeholder_epg
        )
        item {
            var isSubscribing by remember { mutableStateOf(false) }
            var showSuccess by remember { mutableStateOf(false) }
            val coroutineScope = rememberCoroutineScope()

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isSubscribing = true
                            showSuccess = false
                            try {
                                this@epgPageConfiguration.subscribe()
                                showSuccess = true
                                // Auto-hide success message after 3 seconds
                                kotlinx.coroutines.delay(3000)
                                showSuccess = false
                            } catch (e: Exception) {
                                android.util.Log.e("EPG_SUBSCRIBE", "Subscription failed", e)
                            } finally {
                                isSubscribing = false
                            }
                        }
                    },
                    enabled = !isSubscribing,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text(
                        text = if (isSubscribing) {
                            "SUBSCRIBING..."
                        } else {
                            stringResource(R.string.feat_setting_label_subscribe).uppercase()
                        },
                    )
                }

                // Success/Progress message
                if (showSuccess) {
                    Text(
                        text = "✓ EPG subscription successful! Downloading TV guide data in background...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }

        // List of subscribed EPG playlists
        item {
            val epgPlaylists by this@epgPageConfiguration.epgs.collectAsStateWithLifecycle()
            val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

            // Debug logging
            android.util.Log.d("EPG_LIST", "EPG playlists: ${epgPlaylists.size}")
            epgPlaylists.forEach { playlist ->
                android.util.Log.d("EPG_LIST", "EPG: ${playlist.title}, url: ${playlist.url}")
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "SUBSCRIBED EPG LISTS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (epgPlaylists.isNotEmpty()) {
                    epgPlaylists.forEach { playlist ->
                        EpgPlaylistItem(
                            playlist = playlist,
                            onDelete = {
                                this@epgPageConfiguration.deleteEpgPlaylist(playlist.url)
                            }
                        )
                    }
                } else {
                    Text(
                        text = "No EPG lists subscribed yet. Add one using the form above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }
        }
    }
}

private fun SettingViewModel.xtreamPageConfiguration(
    scope: LazyListScope
) {
    with(scope) {
        input(
            value = properties.titleState.value,
            onValueChanged = { properties.titleState.value = it },
            placeholder = R.string.feat_setting_placeholder_title
        )
        input(
            value = properties.urlState.value,
            onValueChanged = { properties.urlState.value = it },
            placeholder = R.string.feat_setting_placeholder_url
        )
        input(
            value = properties.usernameState.value,
            onValueChanged = { properties.usernameState.value = it },
            placeholder = R.string.feat_setting_placeholder_username
        )
        input(
            value = properties.passwordState.value,
            onValueChanged = { properties.passwordState.value = it },
            placeholder = R.string.feat_setting_placeholder_password
        )
        item {
            Button(
                onClick = this@xtreamPageConfiguration::subscribe,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.feat_setting_label_subscribe).uppercase(),
                )
            }
        }
    }
}

private fun SettingViewModel.webDropPageConfiguration(
    scope: LazyListScope,
    playlistViewModel: PlaylistViewModel
) {
    with(scope) {
        item {
            val webServerState by playlistViewModel.webServerState.collectAsStateWithLifecycle()

            Column(
                modifier = Modifier.padding(vertical = 16.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
            ) {
                // Info text
                Text(
                    text = stringResource(R.string.feat_setting_webdrop_info),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Server URL (when running)
                if (webServerState.accessUrl != null) {
                    Column(
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.feat_setting_webdrop_access_url),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = webServerState.accessUrl ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Start/Stop button
                Button(
                    onClick = { playlistViewModel.toggleWebServer() }
                ) {
                    Text(
                        text = if (webServerState.isRunning) {
                            stringResource(R.string.feat_setting_webdrop_stop_server)
                        } else {
                            stringResource(R.string.feat_setting_webdrop_start_server)
                        }.uppercase()
                    )
                }
            }
        }
    }
}

private fun managePlaylistsPageConfiguration(
    scope: LazyListScope,
    playlistViewModel: PlaylistViewModel
) {
    with(scope) {
        item {
            val foryouViewModel: com.m3u.business.foryou.ForyouViewModel = hiltViewModel()
            val playlists by foryouViewModel.playlists.collectAsStateWithLifecycle()
            var selectedPlaylistUrl by remember { mutableStateOf<String?>(null) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 16.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(24.dp)
            ) {
                // Info text
                Text(
                    text = "Manage your playlists: select a playlist to delete it. To hide/unhide categories, use the Settings in the playlist view.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (playlists.isEmpty()) {
                    Text(
                        text = "No playlists available. Add a playlist first from the tabs above.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp)
                    )
                } else {
                    // Playlist selector cards (horizontal)
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        playlists.keys.forEach { playlist ->
                            item {
                                PlaylistManagementCard(
                                    playlist = playlist,
                                    isSelected = playlist.url == selectedPlaylistUrl,
                                    onClick = {
                                        selectedPlaylistUrl = if (selectedPlaylistUrl == playlist.url) null
                                        else playlist.url
                                    }
                                )
                            }
                        }
                    }

                    // Show management options when a playlist is selected
                    selectedPlaylistUrl?.let { playlistUrl ->
                        val selectedPlaylist = playlists.keys.find { it.url == playlistUrl }
                        val channelCount = playlists[selectedPlaylist] ?: 0
                        if (selectedPlaylist != null) {
                            // Use a separate composable for full management
                            FullPlaylistManagementView(
                                playlist = selectedPlaylist,
                                channelCount = channelCount,
                                playlistUrl = playlistUrl,
                                foryouViewModel = foryouViewModel,
                                playlistViewModel = playlistViewModel,
                                onPlaylistDeleted = { selectedPlaylistUrl = null }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.input(
    value: String,
    onValueChanged: (String) -> Unit,
    @StringRes placeholder: Int
) {
    item {
        TextField(
            value = value,
            onValueChange = onValueChanged,
            placeholder = stringResource(placeholder).uppercase()
        )
    }
}

@Composable
private fun EpgPlaylistItem(
    playlist: com.m3u.data.database.model.Playlist,
    onDelete: () -> Unit
) {
    // Non-focusable container - only DELETE button is focusable
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = JetStreamCardShape
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = playlist.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Only the DELETE button is focusable in the entire row
            Button(
                onClick = onDelete,
                modifier = Modifier.height(40.dp),
                colors = androidx.tv.material3.ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("DELETE")
            }
        }
    }
}