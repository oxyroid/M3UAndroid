package com.m3u.features.console.command

import kotlinx.coroutines.flow.Flow

abstract class CommandHandler {
    abstract fun intercept(handler: CommandHandler? = null)
    abstract fun execute(): Flow<CommandResource<String>>
    abstract val introduce: String
}

sealed class CommandResource<out T> {
    object Idle : CommandResource<Nothing>()
    data class Output(val line: String) : CommandResource<String>()
}