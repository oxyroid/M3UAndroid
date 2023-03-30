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
import com.m3u.data.local.entity.Live
import com.m3u.features.feed.R
import com.m3u.ui.components.SheetDialog
import com.m3u.ui.components.SheetItem
import com.m3u.ui.components.SheetTitle
import com.m3u.ui.model.LocalSpacing

@Composable
internal fun FeedDialog(
    state: DialogState,
    onUpdate: (DialogState) -> Unit,
    onFavorite: (Int, Boolean) -> Unit,
    onMute: (Int, Boolean) -> Unit,
    onSavePicture: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    SheetDialog(
        visible = state != DialogState.Idle,
        onDismiss = {
            onUpdate(DialogState.Idle)
        },
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.small),
        content = {
            state as DialogState.Menu
            SheetTitle(state.live.title)
            val favourite = state.live.favourite
            SheetItem(
                text = stringResource(
                    if (favourite) R.string.dialog_favourite_cancel_title
                    else R.string.dialog_favourite_title
                ),
                onClick = {
                    onUpdate(DialogState.Idle)
                    onFavorite(state.live.id, !favourite)
                },
                modifier = Modifier.fillMaxWidth()
            )
            SheetItem(
                text = stringResource(R.string.dialog_mute_title),
                onClick = {
                    onUpdate(DialogState.Idle)
                    onMute(state.live.id, true)
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (!state.live.cover.isNullOrEmpty()) {
                SheetItem(
                    text = stringResource(R.string.dialog_save_picture_title),
                    onClick = {
                        onUpdate(DialogState.Idle)
                        onSavePicture(state.live.id)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

internal sealed class DialogState {
    object Idle : DialogState()
    data class Menu(val live: Live) : DialogState()
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