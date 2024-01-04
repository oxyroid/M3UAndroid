package com.m3u.features.console.command.impl

import com.m3u.features.console.command.ParamCommandHandler

internal class CrashCommandHandler(input: String) : ParamCommandHandler(input) {
    override val introduce: String
        get() = """
            Welcome to Crash Command Handler
            - crash: throw an uncaught error.
            ~
        """.trimIndent()

    companion object {
        const val KEY = "crash"
    }
}