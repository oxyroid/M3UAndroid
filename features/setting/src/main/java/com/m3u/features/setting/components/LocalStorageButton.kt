package com.m3u.features.setting.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.m3u.core.util.readFileName
import com.m3u.i18n.R.string
import com.m3u.material.model.LocalSpacing

@Composable
internal fun LocalStorageButton(
    uri: Uri,
    onTitle: (String) -> Unit,
    openDocument: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selected = uri != Uri.EMPTY
    val spacing = LocalSpacing.current
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
        uri?.readFileName(context.contentResolver).orEmpty()
    } else stringResource(string.feat_setting_label_select_from_local_storage)
    val color = MaterialTheme.colorScheme.surfaceVariant
    val contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(color)
            .height(48.dp)
            .fillMaxWidth()
            .clickable(
                onClick = {
                    launcher.launch("audio/*")
                },
                enabled = true,
                role = Role.Button
            )
            .padding(
                horizontal = spacing.medium,
                vertical = 12.5.dp
            )
            .then(modifier)
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            fontWeight = FontWeight.SemiBold
        )
        Icon(
            imageVector = icon,
            contentDescription = null
        )
    }
}
