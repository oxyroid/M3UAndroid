package com.m3u.features.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.util.toast
import com.m3u.ui.components.basic.M3UColumn
import com.m3u.ui.components.basic.M3USpacer
import com.m3u.ui.components.basic.M3UTextButton
import com.m3u.ui.components.basic.M3UTextField
import com.m3u.ui.local.LocalSpacing
import com.m3u.ui.local.LocalTheme

@Composable
internal fun SettingRoute(
    modifier: Modifier = Modifier,
    viewModel: SettingViewModel = hiltViewModel()
) {
    val state by viewModel.readable.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state.message) {
        state.message.handle {
            context.toast(it)
        }
    }
    SettingScreen(
        addEnabled = !state.adding,
        onParseUrl = { viewModel.onEvent(SettingEvent.OnUrlSubmit(it)) },
        appVersion = state.appVersion,
        modifier = modifier
    )
}

@Composable
private fun SettingScreen(
    addEnabled: Boolean,
    appVersion: String,
    onParseUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue())
    }
    M3UColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("features:setting"),
        verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.small)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            M3UTextField(
                textFieldValue = textFieldValue,
                placeholder = stringResource(R.string.url_placeholder),
                modifier = Modifier.weight(1f),
                onValueChange = { textFieldValue = it },
                keyboardActions = KeyboardActions(
                    onDone = {
                        onParseUrl(textFieldValue.text)
                    }
                )
            )
            M3USpacer()
            M3UTextButton(
                enabled = addEnabled,
                text = stringResource(R.string.subscribe),
                onClick = { onParseUrl(textFieldValue.text) }
            )
        }
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            val preText = stringResource(R.string.app_version_pre)
            val text = remember(appVersion) {
                "$preText$appVersion"
            }
            Text(
                text = text,
                style = MaterialTheme.typography.subtitle2,
                textDecoration = TextDecoration.Underline,
                color = LocalTheme.current.primary
            )
        }
    }
}