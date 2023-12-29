package com.m3u.features.foryou.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.m3u.data.database.entity.Playlist
import com.m3u.i18n.R.string
import com.m3u.material.components.AppDialog
import com.m3u.material.components.DialogItem
import com.m3u.material.components.DialogTextField
import com.m3u.material.model.LocalSpacing

internal typealias OnUpdateStatus = (ForyouDialog) -> Unit
internal typealias OnUnsubscribe = (playlistUrl: String) -> Unit
internal typealias OnRename = (playlistUrl: String, target: String) -> Unit

internal sealed class ForyouDialog {
    data object Idle : ForyouDialog()
    data class Selections(
        val playlist: Playlist
    ) : ForyouDialog()
}

@Composable
internal fun ForyouDialog(
    status: ForyouDialog,
    update: OnUpdateStatus,
    unsubscribe: OnUnsubscribe,
    rename: OnRename,
    modifier: Modifier = Modifier
) {
    var editMode by remember { mutableStateOf(false) }
    val borderWidth by animateDpAsState(
        targetValue = if (editMode) 6.dp else 2.dp,
        label = "foryou-dialog-border"
    )
    AppDialog(
        visible = status is ForyouDialog.Selections,
        onDismiss = {
            if (!editMode) {
                update(ForyouDialog.Idle)
            }
        },
        border = BorderStroke(
            borderWidth,
            MaterialTheme.colorScheme.outline
        ),
        verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.small),
        modifier = modifier,
        content = {
            val theme = MaterialTheme.colorScheme
            val context = LocalContext.current
            val currentStatus = remember { status as ForyouDialog.Selections }
            if (status is ForyouDialog.Selections) {
                val editable = with(currentStatus.playlist) {
                    !local || title.isNotEmpty()
                }
                var renamedText by remember(currentStatus) {
                    mutableStateOf(
                        with(currentStatus.playlist) {
                            if (editable) title else context.getString(string.feat_foryou_imported_playlist_title)
                        }
                    )
                }
                DialogTextField(
                    text = renamedText,
                    onTextChange = { renamedText = it },
                    readOnly = !editMode,
                    icon = Icons.Rounded.Edit.takeIf { editable },
                    iconTint = if (editMode) theme.primary else theme.onBackground,
                    onIconClick = {
                        val target = !editMode
                        if (!target && renamedText != currentStatus.playlist.title) {
                            rename(currentStatus.playlist.url, renamedText)
                        }
                        editMode = target
                    }
                )
                if (!editMode) {
                    DialogItem(string.feat_foryou_unsubscribe_playlist) {
                        unsubscribe(currentStatus.playlist.url)
                        update(ForyouDialog.Idle)
                    }
                    if (!currentStatus.playlist.local) {
                        val clipboardManager = LocalClipboardManager.current
                        DialogItem(string.feat_foryou_copy_playlist_url) {
                            val annotatedString = AnnotatedString(currentStatus.playlist.url)
                            clipboardManager.setText(annotatedString)
                            update(ForyouDialog.Idle)
                        }
                    }
                }
            }
        }
    )
}
