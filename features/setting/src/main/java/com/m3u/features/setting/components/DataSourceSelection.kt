package com.m3u.features.setting.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.m3u.i18n.R.string
import com.m3u.material.components.ClickableSelection
import kotlinx.collections.immutable.ImmutableList

internal enum class DataSource(
    @StringRes val resId: Int,
    val supported: Boolean = false
) {
    M3U(string.feat_setting_data_source_m3u, true),
    Xtream(string.feat_setting_data_source_xtream),
    Emby(string.feat_setting_data_source_emby),
    Dropbox(string.feat_setting_data_source_dropbox),
    Aliyun(string.feat_setting_data_source_aliyun)
}

@Composable
internal fun DataSourceSelection(
    selected: DataSource,
    onSelected: (DataSource) -> Unit,
    supported: ImmutableList<DataSource>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ClickableSelection(
            onClick = { expanded = true },
            modifier = modifier
        ) {
            Text(stringResource(selected.resId))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            supported.forEach { current ->
                DropdownMenuItem(
                    text = { Text(stringResource(current.resId)) },
                    enabled = current.supported,
                    onClick = {
                        onSelected(current)
                        expanded = false
                    }
                )
            }
        }
    }
}