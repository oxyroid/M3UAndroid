package com.m3u.features.console

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.BaseViewModel
import com.m3u.core.architecture.PackageProvider
import com.m3u.features.console.command.CommandHandler
import com.m3u.features.console.command.CommandResource
import com.m3u.features.console.command.EmptyCommandHandler
import com.m3u.features.console.command.LoggerCommandHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConsoleViewModel @Inject constructor(
    application: Application,
    provider: PackageProvider
) : BaseViewModel<ConsoleState, ConsoleEvent>(
    application = application,
    emptyState = ConsoleState()
) {
    init {
        viewModelScope.launch {
            delay(2000)
            val message = """
                > Console Editor
                version: ${provider.getVersionName()}
                debug: ${provider.isDebug()},
                applicationId: ${provider.getApplicationID()}
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
        writable.update { ConsoleState() }
    }

    private fun findCommandHandler(input: String): CommandHandler {
        return when (CommandHandler.parseKey(input)) {
            "logger" -> LoggerCommandHandler(input)
            else -> EmptyCommandHandler
        }
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