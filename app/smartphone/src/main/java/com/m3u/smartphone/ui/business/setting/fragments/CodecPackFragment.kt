package com.m3u.smartphone.ui.business.setting.fragments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.m3u.business.setting.CodecPackState
import com.m3u.core.util.basic.title
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.material.components.Preference
import com.m3u.smartphone.ui.material.ktx.plus
import com.m3u.smartphone.ui.material.model.LocalSpacing

@Composable
internal fun CodecPackFragment(
    state: CodecPackState,
    onInstall: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(spacing.small),
        contentPadding = contentPadding + PaddingValues(spacing.medium),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            Preference(
                title = stringResource(string.feat_setting_codec_pack_status).title(),
                content = if (state.installed) {
                    stringResource(string.feat_setting_codec_pack_installed)
                } else {
                    stringResource(string.feat_setting_codec_pack_not_installed)
                },
                icon = if (state.installed) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                trailing = {
                    if (state.installing) {
                        CircularProgressIndicator()
                    }
                }
            )
        }
        item {
            Preference(
                title = stringResource(string.feat_setting_codec_pack_id).title(),
                content = state.packId,
                icon = Icons.Rounded.Memory
            )
        }
        item {
            Preference(
                title = stringResource(string.feat_setting_codec_pack_abi).title(),
                content = state.abi ?: stringResource(string.feat_setting_codec_pack_abi_unknown),
                icon = Icons.Rounded.Memory
            )
        }
        if (!state.error.isNullOrBlank()) {
            item {
                Preference(
                    title = stringResource(string.feat_setting_codec_pack_error).title(),
                    content = state.error,
                    icon = Icons.Rounded.Error
                )
            }
        }
        item {
            Button(
                enabled = state.enabled && !state.installing,
                onClick = onInstall
            ) {
                Icon(imageVector = Icons.Rounded.Download, contentDescription = null)
                Text(
                    text = stringResource(
                        if (state.installed) string.feat_setting_codec_pack_verify
                        else string.feat_setting_codec_pack_download
                    ).title()
                )
            }
        }
        item {
            OutlinedButton(
                enabled = state.enabled && !state.installing,
                onClick = onRefresh
            ) {
                Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null)
                Text(text = stringResource(string.feat_setting_codec_pack_refresh).title())
            }
        }
        if (state.installed) {
            item {
                OutlinedButton(
                    enabled = state.enabled && !state.installing,
                    onClick = onDelete
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = stringResource(string.feat_setting_codec_pack_delete).title(),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}