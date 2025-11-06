package com.m3u.tv.screens.profile

import android.view.KeyEvent.KEYCODE_DPAD_UP
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
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
        DataSource.M3U,
        DataSource.EPG,
        DataSource.Xtream,
        DataSource.WebDrop
    )

    val focusRequesters = remember { List(size = dataSources.size + 1) { FocusRequester() } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = childPadding.start)
    ) {
        item {
            val (parent, child) = createInitialFocusRestorerModifiers()
            val tabIndex =
                remember(properties.selectedState.value) { dataSources.indexOf(properties.selectedState.value) }
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
                    val isSelected = dataSource == properties.selectedState.value
                    Tab(
                        selected = isSelected,
                        onFocus = { properties.selectedState.value = dataSource },
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
            }
        }

        when (properties.selectedState.value) {
            DataSource.M3U -> m3uPageConfiguration(this)
            DataSource.EPG -> epgPageConfiguration(this)
            DataSource.Xtream -> xtreamPageConfiguration(this)
            DataSource.WebDrop -> webDropPageConfiguration(this, playlistViewModel)
            else -> {}
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
    scope: LazyListScope
) {
    with(scope) {
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
            Button(
                onClick = this@epgPageConfiguration::subscribe,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.feat_setting_label_subscribe).uppercase(),
                )
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