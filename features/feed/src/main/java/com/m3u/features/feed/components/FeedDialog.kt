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
import com.m3u.ui.components.AlertDialog
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
    val title = when (state) {
        DialogState.Idle -> ""
        is DialogState.Menu -> ""
        is DialogState.Favorite -> if (state.live.favourite) stringResource(R.string.dialog_favourite_cancel_title)
        else stringResource(R.string.dialog_favourite_title)
        is DialogState.Mute -> stringResource(R.string.dialog_mute_title)
        is DialogState.SavePicture -> ""
    }

    val content = when (state) {
        DialogState.Idle -> ""
        is DialogState.Menu -> ""
        is DialogState.Favorite -> stringResource(R.string.dialog_favourite_content)
        is DialogState.Mute -> stringResource(R.string.dialog_mute_content)
        is DialogState.SavePicture -> ""
    }
    val confirm = when (state) {
        DialogState.Idle -> null
        is DialogState.Menu -> null
        is DialogState.Favorite -> stringResource(R.string.dialog_favourite_confirm)
        is DialogState.Mute -> stringResource(R.string.dialog_mute_confirm)
        is DialogState.SavePicture -> null
    }
    val dismiss = when (state) {
        DialogState.Idle -> null
        is DialogState.Menu -> null
        is DialogState.Favorite -> stringResource(R.string.dialog_favourite_dismiss)
        is DialogState.Mute -> stringResource(R.string.dialog_mute_dismiss)
        is DialogState.SavePicture -> null
    }

    fun onConfirm() = when (state) {
        DialogState.Idle -> {}
        is DialogState.Menu -> {}
        is DialogState.Favorite -> {
            onUpdate(DialogState.Idle)
            onFavorite(state.live.id, !state.live.favourite)
        }
        is DialogState.Mute -> {
            onUpdate(DialogState.Idle)
            // TODO
            onMute(state.live.id, true)
        }
        is DialogState.SavePicture -> {}
    }

    fun onDismiss() = when (state) {
        DialogState.Idle -> {}
        is DialogState.Menu -> onUpdate(DialogState.Idle)
        is DialogState.Favorite -> onUpdate(DialogState.Menu(state.live))
        is DialogState.Mute -> onUpdate(DialogState.Menu(state.live))
        is DialogState.SavePicture -> {}
    }

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
                        DialogItem(
                            resId = if (state.live.favourite) R.string.dialog_favourite_cancel_title
                            else R.string.dialog_favourite_title,
                            onUpdate = {
                                if (state.live.favourite) {
                                    onUpdate(DialogState.Idle)
                                    onFavorite(state.live.id, false)
                                } else {
                                    onUpdate(DialogState.Favorite(state.live))
                                }
                            },
                        )
                        DialogItem(
                            resId = R.string.dialog_mute_title,
                            onUpdate = { onUpdate(DialogState.Mute(state.live)) },
                        )

                        DialogItem(resId = R.string.dialog_save_picture_title, onUpdate = {
                            onUpdate(DialogState.Idle)
                            onSavePicture(state.live.id)
                        })
                    }
                }
            }
        }
        else -> {
            AlertDialog(
                title = title,
                text = content,
                confirm = confirm,
                dismiss = dismiss,
                onDismissRequest = { onUpdate(DialogState.Idle) },
                onConfirm = ::onConfirm,
                onDismiss = ::onDismiss,
                modifier = modifier
            )
        }
    }
}

internal sealed class DialogState {
    object Idle : DialogState()
    data class Menu(val live: Live) : DialogState()
    data class Favorite(val live: Live) : DialogState()
    data class Mute(val live: Live) : DialogState()
    data class SavePicture(val live: Live) : DialogState()
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