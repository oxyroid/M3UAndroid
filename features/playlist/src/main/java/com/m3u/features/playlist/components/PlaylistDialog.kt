package com.m3u.features.playlist.components

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.m3u.data.database.entity.Stream
import com.m3u.i18n.R.string
import com.m3u.material.components.AppDialog
import com.m3u.material.components.DialogItem
import com.m3u.material.components.DialogTextField
import com.m3u.material.model.LocalSpacing

internal typealias OnUpdateDialogStatus = (DialogStatus) -> Unit
internal typealias OnFavoriteStream = (streamId: Int, target: Boolean) -> Unit
internal typealias OnBannedStream = (streamId: Int, target: Boolean) -> Unit
internal typealias OnSavePicture = (streamId: Int) -> Unit

@Composable
internal fun PlaylistDialog(
    status: DialogStatus,
    onUpdate: OnUpdateDialogStatus,
    onFavorite: OnFavoriteStream,
    onBanned: OnBannedStream,
    onSavePicture: OnSavePicture,
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
            val favourite = status.stream.favourite
            DialogItem(
                if (favourite) string.feat_playlist_dialog_favourite_cancel_title
                else string.feat_playlist_dialog_favourite_title
            ) {
                onUpdate(DialogStatus.Idle)
                onFavorite(status.stream.id, !favourite)
            }
            DialogItem(string.feat_playlist_dialog_mute_title) {
                onUpdate(DialogStatus.Idle)
                onBanned(status.stream.id, true)
            }
            if (!status.stream.cover.isNullOrEmpty()) {
                DialogItem(string.feat_playlist_dialog_save_picture_title) {
                    onUpdate(DialogStatus.Idle)
                    onSavePicture(status.stream.id)
                }
            }
        }
    }
}

internal sealed class DialogStatus {
    data object Idle : DialogStatus()
    data class Selections(val stream: Stream) : DialogStatus()
}

@Composable
private fun DialogItem(
    @StringRes resId: Int,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = Modifier
            .semantics(mergeDescendants = true) { }
            .fillMaxWidth()
            .clickable(onClick = onUpdate)
            .then(modifier)
            .padding(LocalSpacing.current.medium)
    ) {
        Text(
            text = stringResource(resId),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Bold
        )
    }
}