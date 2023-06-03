package com.m3u.features.setting.parts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.m3u.data.database.entity.Live
import com.m3u.features.setting.R
import com.m3u.features.setting.components.MutedLiveItem
import com.m3u.ui.components.Button
import com.m3u.ui.components.LabelField
import com.m3u.ui.components.TextButton
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalTheme

@Composable
internal fun FeedsPart(
    title: String,
    url: String,
    mutedLives: List<Live>,
    onBannedLive: (Int) -> Unit,
    enabled: Boolean,
    onTitle: (String) -> Unit,
    onUrl: (String) -> Unit,
    onSubscribe: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val theme = LocalTheme.current
    val focusRequester = remember { FocusRequester() }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(spacing.small),
        modifier = modifier.padding(spacing.medium)
    ) {
        if (mutedLives.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(spacing.medium)),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = stringResource(R.string.label_muted_lives),
                        style = MaterialTheme.typography.button,
                        color = theme.onTint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(theme.tint)
                            .padding(
                                vertical = spacing.extraSmall,
                                horizontal = spacing.medium
                            )
                    )
                    mutedLives.forEach { live ->
                        MutedLiveItem(
                            live = live,
                            onBannedLive = { onBannedLive(live.id) },
                            modifier = Modifier.background(theme.surface)
                        )
                    }
                }
            }
        }

        item {
            LabelField(
                text = title,
                enabled = enabled,
                placeholder = stringResource(R.string.placeholder_title).uppercase(),
                onValueChange = onTitle,
                keyboardActions = KeyboardActions(
                    onNext = {
                        focusRequester.requestFocus()
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            LabelField(
                text = url,
                enabled = enabled,
                placeholder = stringResource(R.string.placeholder_url).uppercase(),
                onValueChange = onUrl,
                keyboardActions = KeyboardActions(
                    onDone = {
                        onSubscribe()
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }

        item {
            val resId = if (enabled) R.string.label_subscribe else R.string.label_subscribing
            Button(
                enabled = enabled,
                text = stringResource(resId),
                onClick = onSubscribe,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            val subscribeFromClipboardTextResId =
                if (enabled) R.string.label_parse_from_clipboard else R.string.label_subscribing
            val clipboardManager = LocalClipboardManager.current
            TextButton(
                text = stringResource(subscribeFromClipboardTextResId),
                enabled = enabled,
                onClick = {
                    val clipboardUrl = clipboardManager.getText()?.text.orEmpty()
                    val clipboardTitle = run {
                        val filePath = clipboardUrl.split("/")
                        val fileSplit = filePath.lastOrNull()?.split(".") ?: emptyList()
                        fileSplit.firstOrNull() ?: "Feed_${System.currentTimeMillis()}"
                    }
                    onTitle(clipboardTitle)
                    onUrl(clipboardUrl)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

//        item {
//            val context = LocalContext.current
//            val launcher =
//                rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { file ->
//                    file ?: return@rememberLauncherForActivityResult
//                    val text = context.contentResolver.openInputStream(file)?.use {
//                        it.bufferedReader().readText()
//                    }.orEmpty()
//                    context.toast(text)
//                }
//            val subscribeFromDiskTextResId = if (enabled) R.string.label_parse_from_disk
//            else R.string.label_subscribing
//            TextButton(
//                text = stringResource(subscribeFromDiskTextResId),
//                enabled = enabled,
//                onClick = {
//                    launcher.launch(arrayOf("text/plain"))
//                },
//                modifier = Modifier.fillMaxWidth()
//            )
//        }
    }
}

