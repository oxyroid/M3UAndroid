package com.m3u.smartphone.ui.business.setting.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.m3u.business.setting.SettingProperties
import com.m3u.data.repository.webserver.WebServerState
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.material.model.LocalSpacing

@Composable
context(properties: SettingProperties)
internal fun WebDropInputContent(
    webServerState: WebServerState,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onCopyUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        // Status Indicator
        Surface(
            shape = MaterialTheme.shapes.small,
            color = when {
                webServerState.error != null -> MaterialTheme.colorScheme.errorContainer
                webServerState.isRunning -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Circle,
                    contentDescription = null,
                    tint = when {
                        webServerState.error != null -> MaterialTheme.colorScheme.error
                        webServerState.isRunning -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline
                    },
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = when {
                        webServerState.error != null ->
                            stringResource(string.feat_setting_webdrop_status_error, webServerState.error ?: "Unknown")
                        webServerState.isRunning ->
                            stringResource(string.feat_setting_webdrop_status_running)
                        else ->
                            stringResource(string.feat_setting_webdrop_status_stopped)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        webServerState.error != null -> MaterialTheme.colorScheme.onErrorContainer
                        webServerState.isRunning -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        // URL Display (only when running)
        AnimatedVisibility(visible = webServerState.accessUrl != null) {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        webServerState.accessUrl?.let {
                            clipboardManager.setText(AnnotatedString(it))
                            onCopyUrl(it)
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.medium),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(string.feat_setting_webdrop_access_url),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = webServerState.accessUrl ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = {
                            webServerState.accessUrl?.let {
                                clipboardManager.setText(AnnotatedString(it))
                                onCopyUrl(it)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(string.feat_setting_webdrop_copy_url)
                        )
                    }
                }
            }
        }

        // Control Button
        FilledTonalButton(
            onClick = {
                if (webServerState.isRunning) {
                    onStopServer()
                } else {
                    onStartServer()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = if (webServerState.isRunning) {
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            } else {
                ButtonDefaults.filledTonalButtonColors()
            }
        ) {
            Icon(
                imageVector = if (webServerState.isRunning) {
                    Icons.Rounded.Stop
                } else {
                    Icons.Rounded.CloudUpload
                },
                contentDescription = null
            )
            Spacer(Modifier.width(spacing.small))
            Text(
                text = if (webServerState.isRunning) {
                    stringResource(string.feat_setting_webdrop_stop_server)
                } else {
                    stringResource(string.feat_setting_webdrop_start_server)
                }
            )
        }

        // Info Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.small),
            horizontalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = stringResource(string.feat_setting_webdrop_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
