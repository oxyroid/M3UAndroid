package com.m3u.features.setting.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import com.m3u.material.components.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.m3u.core.util.readFileName
import com.m3u.i18n.R.string
import com.m3u.material.components.ToggleableSelection

@Composable
internal fun LocalStorageButton(
    uri: Uri,
    onTitle: (String) -> Unit,
    openDocument: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selected = uri != Uri.EMPTY
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { result ->
        if (result == null) {
            openDocument(Uri.EMPTY)
            onTitle("")
        } else {
            try {
                val filename = result.readFileName(context.contentResolver)
                    ?: "Playlist_${System.currentTimeMillis()}"
                val title = filename
                    .split(".")
                    .dropLast(1)
                    .joinToString(separator = "", prefix = "", postfix = "")
                onTitle(title)
            } catch (ignored: Exception) {
            }
            openDocument(result)
        }
        openDocument(result ?: Uri.EMPTY)
    }
    val icon = Icons.AutoMirrored.Rounded.OpenInNew
    val text = if (selected) remember(uri) {
        uri.readFileName(context.contentResolver).orEmpty()
    } else stringResource(string.feat_setting_label_select_from_local_storage)

    ToggleableSelection(
        checked = false,
        color = MaterialTheme.colorScheme.surfaceVariant,
        onChanged = { launcher.launch("audio/*") },
        modifier = modifier
    ) {
        Text(text.uppercase())
        Icon(
            imageVector = icon,
            contentDescription = null
        )
    }
}
