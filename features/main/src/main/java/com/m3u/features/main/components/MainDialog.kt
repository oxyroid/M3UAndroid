package com.m3u.features.main.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
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
import com.m3u.data.database.entity.Feed
import com.m3u.ui.components.AppDialog
import com.m3u.ui.components.DialogItem
import com.m3u.ui.components.DialogTextField
import com.m3u.ui.ktx.animateDp
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalTheme
import com.m3u.i18n.R as I18R

internal typealias OnUpdateStatus = (MainDialog) -> Unit
internal typealias OnUnsubscribe = (feedUrl: String) -> Unit
internal typealias OnRename = (feedUrl: String, target: String) -> Unit

internal sealed class MainDialog {
    data object Idle : MainDialog()
    data class Selections(
        val feed: Feed
    ) : MainDialog()
}

@Composable
internal fun MainDialog(
    status: MainDialog,
    update: OnUpdateStatus,
    unsubscribe: OnUnsubscribe,
    rename: OnRename,
    modifier: Modifier = Modifier
) {
    var editMode by remember { mutableStateOf(false) }
    val borderWidth by animateDp("MainDialogBorder") { if (editMode) 6.dp else 2.dp }
    AppDialog(
        visible = status is MainDialog.Selections,
        onDismiss = {
            if (!editMode) {
                update(MainDialog.Idle)
            }
        },
        border = BorderStroke(
            borderWidth,
            LocalTheme.current.divider.copy(alpha = 0.45f)
        ),
        verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.small),
        modifier = modifier,
        content = {
            val theme = LocalTheme.current
            val context = LocalContext.current
            val currentStatus = remember { status as MainDialog.Selections }
            if (status is MainDialog.Selections) {
                val editable = with(currentStatus.feed) {
                    !local || title.isNotEmpty()
                }
                var renamedText by remember(currentStatus) {
                    mutableStateOf(
                        with(currentStatus.feed) {
                            if (editable) title else context.getString(I18R.string.feat_main_imported_feed_title)
                        }
                    )
                }
                DialogTextField(
                    text = renamedText,
                    onTextChange = { renamedText = it },
                    readOnly = !editMode,
                    icon = Icons.Rounded.Edit.takeIf { editable },
                    iconTint = if (editMode) theme.tint else theme.onBackground,
                    onIconClick = {
                        val target = !editMode
                        if (!target && renamedText != currentStatus.feed.title) {
                            rename(currentStatus.feed.url, renamedText)
                        }
                        editMode = target
                    }
                )
                if (!editMode) {
                    DialogItem(I18R.string.feat_main_unsubscribe_feed) {
                        unsubscribe(currentStatus.feed.url)
                        update(MainDialog.Idle)
                    }
                    if (!currentStatus.feed.local) {
                        val clipboardManager = LocalClipboardManager.current
                        DialogItem(I18R.string.feat_main_copy_feed_url) {
                            val annotatedString = AnnotatedString(currentStatus.feed.url)
                            clipboardManager.setText(annotatedString)
                            update(MainDialog.Idle)
                        }
                    }
                }
            }
        }
    )
}
