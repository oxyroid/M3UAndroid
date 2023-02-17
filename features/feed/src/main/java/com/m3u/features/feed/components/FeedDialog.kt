package com.m3u.features.feed.components

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import com.m3u.data.entity.Live
import com.m3u.features.feed.R
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalTheme

@Composable
internal fun FeedDialog(
    state: DialogState,
    onUpdate: (DialogState) -> Unit,
    onFavorite: (Int, Boolean) -> Unit,
    onMute: (Int, Boolean) -> Unit,
    onSavePicture: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        DialogState.Idle -> {}
        is DialogState.Menu -> {
            Dialog(
                onDismissRequest = { onUpdate(DialogState.Idle) }
            ) {
                Card(
                    backgroundColor = LocalTheme.current.background,
                    contentColor = LocalTheme.current.onBackground,
                    modifier = modifier
                ) {
                    Column {
                        val favourite = state.live.favourite
                        DialogItem(
                            resId = if (favourite) R.string.dialog_favourite_cancel_title
                            else R.string.dialog_favourite_title,
                            onUpdate = {
                                onUpdate(DialogState.Idle)
                                onFavorite(state.live.id, !favourite)
                            },
                        )
                        DialogItem(
                            resId = R.string.dialog_mute_title,
                            onUpdate = {
                                onUpdate(DialogState.Idle)
                                // TODO
                                onMute(state.live.id, true)
                            },
                        )

                        DialogItem(
                            resId = R.string.dialog_save_picture_title,
                            onUpdate = {
                                onUpdate(DialogState.Idle)
                                onSavePicture(state.live.id)
                            }
                        )
                    }
                }
            }
        }
    }
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