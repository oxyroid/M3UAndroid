package com.m3u.features.console.command.impl

import com.m3u.features.console.command.ParamCommandHandler
import com.m3u.features.console.command.CommandResource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal data class EmptyCommandHandler(val input: String) : ParamCommandHandler(input) {
    override val introduce: String = """
        !-$input: Cannot recognized "$input", please check word spelling and try again.
    """.trimIndent()

    override fun execute(): Flow<CommandResource<String>> = flow {
        emit(CommandResource.Output(introduce))
        emit(CommandResource.Idle)
    }
}