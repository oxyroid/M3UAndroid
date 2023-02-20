package com.m3u.features.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.ui.components.Background
import com.m3u.ui.components.OuterColumn
import com.m3u.ui.components.TextField
import com.m3u.ui.model.Background
import com.m3u.ui.model.LocalBackground
import com.m3u.ui.model.LocalSpacing

@Composable
internal fun ConsoleRoute(
    modifier: Modifier = Modifier,
    viewModel: ConsoleViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ConsoleScreen(
        modifier = modifier,
        output = state.output,
        input = state.input,
        onInput = { viewModel.onEvent(ConsoleEvent.Input(it)) },
        onExecute = { viewModel.onEvent(ConsoleEvent.Execute) }
    )
}

@Composable
private fun ConsoleScreen(
    input: String,
    output: String,
    onInput: (String) -> Unit,
    onExecute: () -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(
        LocalBackground provides Background(
            color = Color(0xff124439)
        ),
        LocalContentColor provides Color.White
    ) {
        Background {
            OuterColumn(
                modifier = modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.Start
            ) {
                val commands = remember(output) { output.lines() }
                commands.forEach { command ->
                    Text(
                        text = command,
                        style = MaterialTheme.typography.h6,
                        color = if (command.startsWith("> ")) Color.Yellow
                        else Color.Unspecified,
                        modifier = Modifier
                            .padding(horizontal = LocalSpacing.current.medium)
                    )
                }
                TextField(
                    text = input,
                    onValueChange = onInput,
                    singleLine = true,
                    placeholder = "command here...",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(LocalSpacing.current.medium),
                    keyboardActions = KeyboardActions { onExecute() }
                )
            }
        }
    }
}