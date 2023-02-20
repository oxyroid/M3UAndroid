package com.m3u.features.console

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.BaseViewModel
import com.m3u.features.console.command.CommandHandler
import com.m3u.features.console.command.CommandResource
import com.m3u.features.console.command.UpnpCommandHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConsoleViewModel @Inject constructor(
    application: Application
) : BaseViewModel<ConsoleState, ConsoleEvent>(
    application = application,
    emptyState = ConsoleState()
) {
    init {
        viewModelScope.launch {
            delay(2000)
            append("Hello World!")
        }
    }

    override fun onEvent(event: ConsoleEvent) {
        when (event) {
            ConsoleEvent.Execute -> execute()
            is ConsoleEvent.Input -> input(event.text)
        }
    }

    private fun execute() {
        val input = readable.input
        input("")
        append("> $input")
        val lowercaseInput = input.lowercase().trim()
        if (lowercaseInput == "clear") {
            clear()
            return
        }
        val handler = findCommandHandler(lowercaseInput)
        handler.execute()
            .onEach { resource ->
                when (resource) {
                    CommandResource.Idle -> clearFocus()
                    is CommandResource.Output -> {
                        append(resource.line)
                        requestFocus()
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun input(text: String) {
        if (text.contains("\n")) {
            execute()
        } else {
            writable.update {
                it.copy(
                    input = text
                )
            }
        }
    }

    private fun append(text: String) {
        val old = readable.output
        writable.update {
            it.copy(
                output = old + "\n" + text
            )
        }
    }

    private fun clear() {
        writable.update { ConsoleState() }
    }

    private fun findCommandHandler(input: String): CommandHandler {
        val upnpCommandHandler = UpnpCommandHandler(input)
        upnpCommandHandler.intercept()
        return upnpCommandHandler
    }

    private fun requestFocus() {
        writable.update {
            it.copy(
                focus = true
            )
        }
    }

    private fun clearFocus() {
        writable.update {
            it.copy(
                focus = false
            )
        }
    }
}