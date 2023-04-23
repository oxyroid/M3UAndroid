package com.m3u.androidApp.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.m3u.data.database.entity.Post
import com.m3u.ui.components.IconButton
import com.m3u.ui.components.SheetDialog
import com.m3u.ui.components.SheetTextField
import com.m3u.ui.components.TextButton
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalTheme

internal typealias OnDismiss = () -> Unit
internal typealias OnNext = () -> Unit
internal typealias OnPrevious = () -> Unit
internal typealias OnRead = () -> Unit

internal sealed class PostDialogStatus {
    object Idle : PostDialogStatus()
    data class Visible(
        val post: Post,
        val index: Int,
        val total: Int,
    ) : PostDialogStatus()
}

@Composable
internal fun PostDialog(
    status: PostDialogStatus,
    onDismiss: OnDismiss,
    onNext: OnNext,
    onPrevious: OnPrevious,
    onRead: OnRead,
    modifier: Modifier = Modifier,
) {
    val theme = LocalTheme.current
    val spacing = LocalSpacing.current
    SheetDialog(
        visible = status != PostDialogStatus.Idle,
        onDismiss = onDismiss,
        content = {
            when (status) {
                is PostDialogStatus.Visible -> {
                    val post = status.post
                    val index = status.index
                    val total = status.total
                    val type = post.type
                    val icon = when (type) {
                        Post.TYPE_INFO -> Icons.Rounded.Info
                        Post.TYPE_WARNING -> Icons.Rounded.Warning
                        Post.TYPE_RELEASE -> Icons.Rounded.NewReleases
                        else -> Icons.Rounded.DoNotDisturbOn
                    }
                    val tint = when (type) {
                        Post.TYPE_INFO -> Color(0xff1d9bf0)
                        Post.TYPE_WARNING -> Color(0xffffc017)
                        Post.TYPE_RELEASE -> theme.tint
                        else -> theme.divider
                    }
                    SheetTextField(
                        text = post.title,
                        icon = icon,
                        iconTint = tint,
                        onIconClick = {}
                    )
                    Text(
                        text = post.content,
                        color = theme.onSurface,
                        modifier = Modifier.padding(
                            horizontal = spacing.medium,
                            vertical = spacing.small
                        )
                    )
                    Row(
                        modifier = Modifier.padding(
                            horizontal = spacing.medium
                        )
                    ) {
                        IconButton(
                            icon = Icons.Rounded.ChevronLeft,
                            contentDescription = "previous",
                            onClick = onPrevious
                        )
                        TextButton(
                            text = "${index + 1}/$total",
                            // TODO
                            onClick = {  },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            icon = Icons.Rounded.ChevronRight,
                            contentDescription = "next",
                            onClick = onNext
                        )
                    }
                }

                PostDialogStatus.Idle -> {}
            }
        }
    )
}