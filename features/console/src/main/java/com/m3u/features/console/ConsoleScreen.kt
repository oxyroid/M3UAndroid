package com.m3u.features.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.ui.components.Background
import com.m3u.ui.components.MonoText
import com.m3u.ui.components.TextField
import com.m3u.ui.model.LocalHelper
import com.m3u.ui.model.LocalSpacing

@Composable
internal fun ConsoleRoute(
    modifier: Modifier = Modifier,
    viewModel: ConsoleViewModel = hiltViewModel()
) {
    val helper = LocalHelper.current
    val title = stringResource(R.string.console_title)
    SideEffect {
        helper.title = title
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ConsoleScreen(
        modifier = modifier,
        output = state.output,
        input = state.input,
        onInput = { viewModel.onEvent(ConsoleEvent.Input(it)) },
        onExecute = { viewModel.onEvent(ConsoleEvent.Execute) },
        focus = state.focus
    )
}

@Composable
private fun ConsoleScreen(
    focus: Boolean,
    input: String,
    output: String,
    onInput: (String) -> Unit,
    onExecute: () -> Unit,
    modifier: Modifier = Modifier
) {

    Background(
        color = Color.Black,
        contentColor = Color.White
    ) {
        Column {
            val commands = remember(output) { output.lines().asReversed() }
            val state = rememberLazyListState()
            LazyColumn(
                state = state,
                reverseLayout = true,
                modifier = modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.Start,
                contentPadding = PaddingValues(LocalSpacing.current.medium)
            ) {
                items(commands) { command ->
                    CompositionLocalProvider(
                        LocalTextSelectionColors provides TextSelectionColors(
                            backgroundColor = Color(0xff265c8e),
                            handleColor = Color(0xff78c4dd)
                        )
                    ) {
                        SelectionContainer {
                            MonoText(
                                text = MonoStyle.get(command).actual(command),
                                style = MaterialTheme.typography.subtitle2,
                                color = MonoStyle.get(command).color,
                                modifier = Modifier.padding(horizontal = LocalSpacing.current.medium),
                            )
                        }
                    }
                }
            }
            TextField(
                text = input,
                enabled = focus,
                onValueChange = onInput,
                singleLine = true,
                placeholder = "command here...",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = LocalSpacing.current.medium,
                        end = LocalSpacing.current.medium,
                        bottom = LocalSpacing.current.medium,
                    ),
                keyboardActions = KeyboardActions { onExecute() }
            )
            LaunchedEffect(commands) {
                state.animateScrollToItem(0)
            }
        }
    }
}