package com.m3u.features.console.command

import kotlinx.coroutines.channels.ProducerScope

internal interface CommandScope {
    val param: String?
    val arg: String?
    suspend fun output(text: String)
}

internal class CommandProducerScope(
    private val scope: ProducerScope<CommandResource<String>>,
    private val commands: List<String>,
) : CommandScope {
    override val param: String? get() = commands.param
    override val arg: String? get() = commands.arg
    override suspend fun output(text: String) {
        scope.send(CommandResource.Output(text))
    }
}

private val List<String>.param: String? get() = getOrNull(2)
private val List<String>.arg: String? get() = getOrNull(3)
