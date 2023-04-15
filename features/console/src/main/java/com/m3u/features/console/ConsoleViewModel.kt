package com.m3u.features.console

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.m3u.core.annotation.AppPublisherImpl
import com.m3u.core.architecture.BaseViewModel
import com.m3u.core.architecture.Publisher
import com.m3u.core.architecture.reader.FileReader
import com.m3u.features.console.command.CommandHandler
import com.m3u.features.console.command.CommandResource
import com.m3u.features.console.command.impl.EmptyCommandHandler
import com.m3u.features.console.command.impl.LoggerCommandHandler
import com.m3u.features.console.command.impl.UpnpCommandHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ConsoleViewModel @Inject constructor(
    application: Application,
    @AppPublisherImpl publisher: Publisher,
    private val reader: FileReader
) : BaseViewModel<ConsoleState, ConsoleEvent>(
    application = application,
    emptyState = ConsoleState()
) {
    init {
        viewModelScope.launch {
            delay(2000)
            val message = """
                >-Console Editor
                version: ${publisher.versionName}
                debug: ${publisher.debug}
                applicationId: ${publisher.applicationID}
            """.trimIndent()
            append(message)
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
        append(">-$input")
        val lowercaseInput = input.lowercase().trim()
        if (lowercaseInput == "clear") {
            clear()
            return
        }
        val handler = findCommandHandler(lowercaseInput)
        handler.execute()
            .onEach { resource ->
                when (resource) {
                    CommandResource.Idle -> requestFocus()
                    is CommandResource.Output -> {
                        append(resource.line)
                        clearFocus()
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
        writable.value = ConsoleState()
    }

    private fun findCommandHandler(input: String): CommandHandler =
        when (CommandHandler.parseKey(input)) {
            LoggerCommandHandler.KEY -> {
                LoggerCommandHandler(
                    input = input,
                    reader = reader
                )
            }
            UpnpCommandHandler.KEY -> {
                UpnpCommandHandler(
                    discoverNearbyDevices = { flow { } },
                    input = input
                )
            }
            else -> EmptyCommandHandler(input)
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