package com.m3u.smartphone.ui.business.setting.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m3u.core.foundation.util.readFileName
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.material.ktx.safeDisplayText
import com.m3u.smartphone.ui.material.model.LocalSpacing

@Composable
internal fun LocalStorageButton(
    titleState: MutableState<String>,
    uriState: MutableState<Uri>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val uri by uriState
    val selected = uri != Uri.EMPTY
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { result ->
        if (result != null) {
            runCatching {
                result.readFileName(context.contentResolver)
            }.getOrNull()?.takeIf(String::isNotBlank)?.let { filename ->
                val safeFilename = filename.safeDisplayText()
                titleState.value = safeFilename
                    .substringBeforeLast(delimiter = ".", missingDelimiterValue = safeFilename)
                    .ifBlank { safeFilename }
            }
            uriState.value = result
        }
    }
    val pickerLabel = stringResource(string.feat_setting_label_select_from_local_storage)
    val selectedFileName = remember(uri) {
        runCatching {
            uri.readFileName(context.contentResolver)
        }.getOrNull()
    }
    val safeSelectedFileName = selectedFileName
        ?.safeDisplayText()
        ?.takeIf { selected && it.isNotBlank() }
    val text = safeSelectedFileName ?: pickerLabel
    val spacing = LocalSpacing.current

    OutlinedButton(
        onClick = {
            launcher.launch(
                arrayOf(
                    "text/*",
                    "audio/x-mpegurl",
                    "application/x-mpegurl",
                    "application/vnd.apple.mpegurl",
                    "application/octet-stream",
                )
            )
        },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .semantics {
                contentDescription = pickerLabel
                safeSelectedFileName?.let { stateDescription = it }
            },
        contentPadding = PaddingValues(
            horizontal = spacing.medium,
            vertical = spacing.small,
        ),
    ) {
        Icon(
            imageVector = Icons.Rounded.FolderOpen,
            contentDescription = null,
        )
        Spacer(Modifier.size(spacing.small))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge.copy(
                textDirection = if (selected) {
                    TextDirection.ContentOrLtr
                } else {
                    TextDirection.Unspecified
                },
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
