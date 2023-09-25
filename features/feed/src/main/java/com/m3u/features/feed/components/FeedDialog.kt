package com.m3u.features.feed.components

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.m3u.data.database.entity.Live
import com.m3u.features.feed.R
import com.m3u.ui.components.DialogTextField
import com.m3u.ui.components.DialogItem
import com.m3u.ui.components.AppDialog
import com.m3u.ui.model.LocalSpacing

internal typealias OnUpdateDialogStatus = (DialogStatus) -> Unit
internal typealias OnFavoriteLive = (liveId: Int, target: Boolean) -> Unit
internal typealias OnBannedLive = (liveId: Int, target: Boolean) -> Unit
internal typealias OnSavePicture = (liveId: Int) -> Unit

@Composable
internal fun FeedDialog(
    status: DialogStatus,
    onUpdate: OnUpdateDialogStatus,
    onFavorite: OnFavoriteLive,
    onBanned: OnBannedLive,
    onSavePicture: OnSavePicture,
    modifier: Modifier = Modifier
) {
    AppDialog(
        visible = status != DialogStatus.Idle,
        onDismiss = {
            onUpdate(DialogStatus.Idle)
        },
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.small),
        content = {
            if (status is DialogStatus.Selections) {
                DialogTextField(
                    text = status.live.title,
                )
                val favourite = status.live.favourite
                DialogItem(
                    if (favourite) R.string.dialog_favourite_cancel_title
                    else R.string.dialog_favourite_title
                ) {
                    onUpdate(DialogStatus.Idle)
                    onFavorite(status.live.id, !favourite)
                }
                DialogItem(R.string.dialog_mute_title) {
                    onUpdate(DialogStatus.Idle)
                    onBanned(status.live.id, true)
                }
                if (!status.live.cover.isNullOrEmpty()) {
                    DialogItem(R.string.dialog_save_picture_title) {
                        onUpdate(DialogStatus.Idle)
                        onSavePicture(status.live.id)
                    }
                }
            }
        }
    )
}

internal sealed class DialogStatus {
    data object Idle : DialogStatus()
    data class Selections(val live: Live) : DialogStatus()
}

@Composable
private fun DialogItem(
    @StringRes resId: Int,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onUpdate)
            .padding(LocalSpacing.current.medium)
    ) {
        Text(
            text = stringResource(resId),
            style = MaterialTheme.typography.subtitle1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Bold
        )
    }
}