package com.m3u.feature.setting.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.m3u.core.util.readFileName
import com.m3u.i18n.R.string
import com.m3u.material.components.Icon
import com.m3u.material.components.ToggleableSelection

@Composable
internal fun LocalStorageButton(
    titleState: MutableState<String>,
    uriState: MutableState<Uri>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uri by uriState
    val selected = uri != Uri.EMPTY
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { result ->
        if (result == null) {
            titleState.value = ""
            uriState.value = Uri.EMPTY
        } else {
            try {
                val filename = result.readFileName(context.contentResolver)
                    ?: "Playlist_${System.currentTimeMillis()}"
                val title = filename
                    .split(".")
                    .dropLast(1)
                    .joinToString(separator = "", prefix = "", postfix = "")
                titleState.value = title
            } catch (ignored: Exception) {
            }
            uriState.value = result
        }
        uriState.value = result ?: Uri.EMPTY
    }
    val icon = Icons.AutoMirrored.Rounded.OpenInNew
    val text = if (selected) remember(uri) {
        uri.readFileName(context.contentResolver).orEmpty()
    } else stringResource(string.feat_setting_label_select_from_local_storage)

    ToggleableSelection(
        checked = false,
        color = MaterialTheme.colorScheme.surfaceVariant,
        onChanged = {
            launcher.launch(
                arrayOf(
                    "text/*",
                    "video/*",
                    "audio/*",
                    "application/*",
                )
            )
        },
        modifier = modifier
    ) {
        Text(text.uppercase())
        Icon(
            imageVector = icon,
            contentDescription = null
        )
    }
}
