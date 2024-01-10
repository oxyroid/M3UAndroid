package com.m3u.features.favorite.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.m3u.data.database.model.Stream
import com.m3u.i18n.R.string
import com.m3u.material.components.AppDialog
import com.m3u.material.components.DialogItem
import com.m3u.material.components.DialogTextField
import com.m3u.material.model.LocalSpacing

@Composable
internal fun FavoriteDialog(
    status: DialogStatus,
    onUpdate: (DialogStatus) -> Unit,
    cancelFavorite: (streamId: Int) -> Unit,
    createShortcut: (streamId: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    AppDialog(
        visible = status != DialogStatus.Idle,
        onDismiss = {
            onUpdate(DialogStatus.Idle)
        },
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.small)
    ) {
        if (status is DialogStatus.Selections) {
            DialogTextField(
                text = status.stream.title,
            )
            DialogItem(string.feat_playlist_dialog_favourite_cancel_title) {
                onUpdate(DialogStatus.Idle)
                cancelFavorite(status.stream.id)
            }
            DialogItem(string.feat_playlist_dialog_create_shortcut_title) {
                onUpdate(DialogStatus.Idle)
                createShortcut(status.stream.id)
            }
        }
    }
}

internal sealed class DialogStatus {
    data object Idle : DialogStatus()
    data class Selections(val stream: Stream) : DialogStatus()
}
