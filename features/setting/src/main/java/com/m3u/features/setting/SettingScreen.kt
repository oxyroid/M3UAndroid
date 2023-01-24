package com.m3u.features.setting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.ui.components.basic.M3UColumn
import com.m3u.ui.components.basic.M3USpacer
import com.m3u.ui.components.basic.M3UTextButton
import com.m3u.ui.components.basic.M3UTextField

@Composable
internal fun SettingRoute(
    modifier: Modifier = Modifier,
    viewModel: SettingViewModel = hiltViewModel()
) {
    val state by viewModel.readable.collectAsStateWithLifecycle()
    SettingScreen(
        addEnabled = !state.adding,
        onParseUrl = { viewModel.onEvent(SettingEvent.OnUrlSubmit(it)) },
        modifier = modifier
    )
}

@Composable
private fun SettingScreen(
    addEnabled: Boolean,
    onParseUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue())
    }
    M3UColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("features:setting")
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
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
                text = "Add",
                onClick = { onParseUrl(textFieldValue.text) }
            )
        }
    }
}