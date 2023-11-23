package com.m3u.features.setting.fragments

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m3u.core.util.readFileName
import com.m3u.data.database.entity.Live
import com.m3u.features.setting.components.MutedLiveItem
import com.m3u.i18n.R.string
import com.m3u.material.components.Button
import com.m3u.material.components.LabelField
import com.m3u.material.components.TextButton
import com.m3u.material.ktx.plus
import com.m3u.material.model.LocalSpacing

internal typealias MutedLivesFactory = () -> List<Live>

@Composable
internal fun SubscriptionsFragment(
    contentPadding: PaddingValues,
    title: String,
    url: String,
    uriFactory: () -> Uri,
    localStorage: Boolean,
    mutedLivesFactory: MutedLivesFactory,
    onBanned: (Int) -> Unit,
    onTitle: (String) -> Unit,
    onUrl: (String) -> Unit,
    onSubscribe: () -> Unit,
    onLocalStorage: () -> Unit,
    openDocument: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val theme = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    val mutedLives = mutedLivesFactory()
    LazyColumn(
        contentPadding = PaddingValues(spacing.medium) + contentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing.small),
        modifier = modifier.focusGroup()
    ) {
        item {
            if (mutedLives.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(spacing.medium)),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = stringResource(string.feat_setting_label_muted_lives),
                        color = theme.onPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(theme.primary)
                            .padding(
                                vertical = spacing.extraSmall,
                                horizontal = spacing.medium
                            )
                    )
                    mutedLives.forEach { live ->
                        MutedLiveItem(
                            live = live,
                            onBanned = { onBanned(live.id) },
                            modifier = Modifier.background(theme.surface)
                        )
                    }
                }
            }
        }

        item {
            LabelField(
                text = title,
                placeholder = stringResource(string.feat_setting_placeholder_title).uppercase(),
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
            Crossfade(
                targetState = localStorage,
                label = "url"
            ) { localStorage ->
                if (!localStorage) {
                    LabelField(
                        text = url,
                        placeholder = stringResource(string.feat_setting_placeholder_url).uppercase(),
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
                } else {
                    LocalStorageButton(
                        uriFactory = uriFactory,
                        openDocument = openDocument,
                    )
                }
            }
        }

        item {
            LocalStorageSwitch(
                checked = localStorage,
                onChanged = onLocalStorage
            )
        }

        item {
            Column {
                Button(
                    text = stringResource(string.feat_setting_label_subscribe),
                    onClick = onSubscribe,
                    modifier = Modifier.fillMaxWidth()
                )
                ClipboardButton(
                    enabled = !localStorage,
                    onTitle = onTitle,
                    onUrl = onUrl
                )
            }
        }
    }
}

@Composable
fun LocalStorageSwitch(
    checked: Boolean,
    onChanged: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(25))
            .toggleable(
                value = checked,
                onValueChange = { onChanged() },
                role = Role.Checkbox
            )
            .padding(horizontal = spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        Text(
            text = stringResource(string.feat_setting_local_storage).uppercase(),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun LocalStorageButton(
    uriFactory: () -> Uri,
    openDocument: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val uri = uriFactory()
    val context = LocalContext.current
    val selected = uri != Uri.EMPTY
    val theme = MaterialTheme.colorScheme
    val spacing = LocalSpacing.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { openDocument(it ?: Uri.EMPTY) }
    val icon = Icons.AutoMirrored.Rounded.OpenInNew
    val text = if (selected) remember(uri) {
        uri.readFileName(context.contentResolver).orEmpty()
    } else stringResource(string.feat_setting_label_select_from_local_storage)
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(25))
            .background(theme.surface)
            .height(48.dp)
            .fillMaxWidth()
            .toggleable(
                value = selected,
                onValueChange = {
                    launcher.launch("*/*")
                },
                enabled = true,
                role = Role.Checkbox
            )
            .padding(
                horizontal = spacing.medium,
                vertical = 12.5.dp
            )
            .then(modifier)
    ) {
        Text(
            text = text.uppercase(),
            style = TextStyle(
                fontSize = 14.sp,
                fontFamily = MaterialTheme.typography.bodyMedium.fontFamily,
                fontWeight = FontWeight.Medium
            )
        )
        Icon(
            imageVector = icon,
            contentDescription = null
        )
    }
}

@Composable
private fun ClipboardButton(
    enabled: Boolean,
    onTitle: (String) -> Unit,
    onUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    TextButton(
        enabled = enabled,
        text = stringResource(string.feat_setting_label_parse_from_clipboard),
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
        modifier = modifier.fillMaxWidth()
    )
}
