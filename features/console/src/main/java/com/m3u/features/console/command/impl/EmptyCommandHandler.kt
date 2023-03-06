package com.m3u.features.console.command.impl

import com.m3u.features.console.command.CommandHandler

internal data class EmptyCommandHandler(val input: String) : CommandHandler(input, "") {
    override val introduce: String = """
        !-$input: Cannot recognized "$input" as any handler, please check your word spelling.
    """.trimIndent()
}