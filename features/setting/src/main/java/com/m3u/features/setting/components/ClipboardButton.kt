package com.m3u.features.setting.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import com.m3u.i18n.R.string
import com.m3u.material.components.TextButton

@Composable
internal fun ClipboardButton(
    enabled: Boolean,
    onTitle: (String) -> Unit,
    onUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    TextButton(
        enabled = enabled,
        text = stringResource(string.feat_setting_label_parse_from_clipboard),
        onClick = {
            val clipboardUrl = clipboardManager.getText()?.text?.trim()?: return@TextButton
            val clipboardTitle = run {
                val filePath = clipboardUrl.split("/")
                val fileSplit = filePath.lastOrNull()?.split(".") ?: emptyList()
                fileSplit.firstOrNull() ?: "Playlist_${System.currentTimeMillis()}"
            }
            onTitle(clipboardTitle)
            onUrl(clipboardUrl)
        },
        modifier = modifier.fillMaxWidth()
    )
}
