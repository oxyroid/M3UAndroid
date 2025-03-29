package com.m3u.tv.screens.profile

import android.view.KeyEvent.KEYCODE_DPAD_UP
import androidx.annotation.StringRes
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
    val dataSources = listOf(
        DataSource.M3U,
        DataSource.EPG,
        DataSource.Xtream
    )

    val focusRequesters = remember { List(size = dataSources.size + 1) { FocusRequester() } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = childPadding.start)
    ) {
        item {
            val (parent, child) = createInitialFocusRestorerModifiers()
            val tabIndex =
                remember(selectedState.value) { dataSources.indexOf(selectedState.value) }
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
                    val isSelected = dataSource == selectedState.value
                    Tab(
                        selected = isSelected,
                        onFocus = { selectedState.value = dataSource },
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

        when (selectedState.value) {
            DataSource.M3U -> m3uPageConfiguration(this)
            DataSource.EPG -> epgPageConfiguration(this)
            DataSource.Xtream -> xtreamPageConfiguration(this)
            else -> {}
        }
    }
}

private fun SettingViewModel.m3uPageConfiguration(
    scope: LazyListScope
) {
    with(scope) {
        input(
            value = titleState.value,
            onValueChanged = { titleState.value = it },
            placeholder = R.string.feat_setting_placeholder_title
        )
        input(
            value = urlState.value,
            onValueChanged = { urlState.value = it },
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
            value = titleState.value,
            onValueChanged = { titleState.value = it },
            placeholder = R.string.feat_setting_placeholder_epg_title
        )
        input(
            value = epgState.value,
            onValueChanged = { epgState.value = it },
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
            value = titleState.value,
            onValueChanged = { titleState.value = it },
            placeholder = R.string.feat_setting_placeholder_title
        )
        input(
            value = urlState.value,
            onValueChanged = { urlState.value = it },
            placeholder = R.string.feat_setting_placeholder_url
        )
        input(
            value = usernameState.value,
            onValueChanged = { usernameState.value = it },
            placeholder = R.string.feat_setting_placeholder_username
        )
        input(
            value = passwordState.value,
            onValueChanged = { passwordState.value = it },
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