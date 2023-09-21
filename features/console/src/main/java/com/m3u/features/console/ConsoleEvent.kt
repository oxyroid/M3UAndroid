package com.m3u.features.console

sealed class ConsoleEvent {
    data object Execute : ConsoleEvent()
    data class Input(val text: String) : ConsoleEvent()
}
