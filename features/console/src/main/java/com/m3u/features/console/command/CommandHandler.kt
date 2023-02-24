package com.m3u.features.console.command

import androidx.annotation.CallSuper
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

abstract class CommandHandler(
    private val input: String,
    private val key: String,
) {
    private val paths: MutableMap<String, suspend ProducerScope<CommandResource<String>>.(String?) -> Unit> =
        mutableMapOf()

    protected fun configPath(
        path: String,
        block: suspend ProducerScope<CommandResource<String>>.(String?) -> Unit
    ) {
        paths[path] = block
    }

    @CallSuper
    open fun execute(): Flow<CommandResource<String>> = channelFlow {
        val commands = input.split(" ")
        val path = commands.path ?: run {
            send(CommandResource.Output(introduce))
            send(CommandResource.Idle)
            return@channelFlow
        }
        val block = paths[path]
        if (block != null) {
            val resource = CommandResource.Output("Executing $key command...")
            send(resource)
            block(this, commands.param)
        } else {
            val resource =
                CommandResource.Output("Unknown path \"$path\", please try again!")
            send(resource)
            send(CommandResource.Idle)
        }
    }

    abstract val introduce: String

    private val List<String>.key: String get() = first()
    private val List<String>.path: String? get() = getOrNull(1)
    private val List<String>.param: String? get() = getOrNull(2)

    companion object {
        fun parseKey(input: String): String? {
            return input.lowercase().split(" ").firstOrNull()
        }
    }
}

sealed class CommandResource<out T> {
    object Idle : CommandResource<Nothing>()
    data class Output(val line: String) : CommandResource<String>()
}