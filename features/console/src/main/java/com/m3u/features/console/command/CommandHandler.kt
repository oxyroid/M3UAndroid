package com.m3u.features.console.command

import androidx.annotation.CallSuper
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

abstract class CommandHandler(
    private val input: String,
    private val key: String,
) {
    protected var next: CommandHandler? = null

    @CallSuper
    open fun intercept(handler: CommandHandler? = null) {
        val commands = input.split(" ")
        if (commands.isEmpty()) {
            next = EmptyCommandHandler
            return
        }
        val key = commands.key
        if ((key != this.key)) {
            next = handler
            return
        }
        next = null
    }

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
        when (val n = next) {
            null -> {
                val commands = input.split(" ")
                val path = commands.path ?: run {
                    send(CommandResource.Output(introduce))
                    send(CommandResource.Idle)
                    return@channelFlow
                }
                val block = paths[path]
                if (block != null) {
                    val resource =
                        CommandResource.Output("Executing $key command...")
                    send(resource)
                    block(this, commands.param)
                } else {
                    val resource =
                        CommandResource.Output("Unknown path \"$path\", please try again!")
                    send(resource)
                    send(CommandResource.Idle)
                }
            }
            else -> n
                .execute()
                .onEach(::send)
                .launchIn(this)
        }

    }

    abstract val introduce: String

    private val List<String>.key: String get() = first()
    private val List<String>.path: String? get() = getOrNull(1)
    private val List<String>.param: String? get() = getOrNull(2)
}

sealed class CommandResource<out T> {
    object Idle : CommandResource<Nothing>()
    data class Output(val line: String) : CommandResource<String>()
}