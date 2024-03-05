package com.m3u.features.foryou.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Abc
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.m3u.core.util.basic.title
import com.m3u.data.database.model.Playlist
import com.m3u.i18n.R.string
import com.m3u.material.components.AppDialog
import com.m3u.material.components.DialogItem
import com.m3u.material.components.DialogTextField
import com.m3u.material.model.LocalSpacing

internal sealed class ForyouDialogState {
    data object Idle : ForyouDialogState()
    data class Selections(
        val playlist: Playlist
    ) : ForyouDialogState()
}

@Composable
internal fun ForyouDialog(
    status: ForyouDialogState,
    update: (ForyouDialogState) -> Unit,
    unsubscribe: (playlistUrl: String) -> Unit,
    rename: (playlistUrl: String, title: String) -> Unit,
    editUserAgent: (playlistUrl: String, userAgent: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var editMode by remember { mutableStateOf(false) }
    val borderWidth by animateDpAsState(
        targetValue = if (editMode) 6.dp else 2.dp,
        label = "foryou-dialog-border"
    )
    AppDialog(
        visible = status is ForyouDialogState.Selections,
        onDismiss = {
            if (!editMode) {
                update(ForyouDialogState.Idle)
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
            val currentStatus = remember { status as ForyouDialogState.Selections }
            if (status is ForyouDialogState.Selections) {
                var editedTitle by remember(currentStatus.playlist.title) {
                    mutableStateOf(currentStatus.playlist.title)
                }
                var editedUserAgent by remember(currentStatus.playlist.userAgent) {
                    mutableStateOf(currentStatus.playlist.userAgent.orEmpty())
                }
                DialogTextField(
                    text = editedTitle,
                    onTextChange = { editedTitle = it },
                    readOnly = !editMode,
                    leadingIcon = Icons.Rounded.Abc,
                    trainingIcon = Icons.Rounded.Edit,
                    iconTint = if (editMode) theme.primary else theme.onBackground,
                    onTrainingIconClick = {
                        val targetEditMode = !editMode
                        if (!targetEditMode && editedTitle != currentStatus.playlist.title) {
                            rename(currentStatus.playlist.url, editedTitle)
                        }
                        if (!targetEditMode && editedTitle != currentStatus.playlist.userAgent) {
                            editUserAgent(
                                currentStatus.playlist.url,
                                editedUserAgent.ifEmpty { null }
                            )
                        }
                        editMode = targetEditMode
                    }
                )
                DialogTextField(
                    text = editedUserAgent,
                    onTextChange = { editedUserAgent = it },
                    placeholder = stringResource(string.feat_foryou_user_agent).title(),
                    readOnly = !editMode,
                    leadingIcon = Icons.Rounded.VerifiedUser,
                    iconTint = if (editMode) theme.primary else theme.onBackground
                )
                if (!editMode) {
                    DialogItem(string.feat_foryou_unsubscribe_playlist) {
                        unsubscribe(currentStatus.playlist.url)
                        update(ForyouDialogState.Idle)
                    }
                    if (!currentStatus.playlist.fromLocal) {
                        val clipboardManager = LocalClipboardManager.current
                        DialogItem(string.feat_foryou_copy_playlist_url) {
                            val annotatedString = AnnotatedString(currentStatus.playlist.url)
                            clipboardManager.setText(annotatedString)
                            update(ForyouDialogState.Idle)
                        }
                    }
                }
            }
        }
    )
}
