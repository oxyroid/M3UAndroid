package com.m3u.features.console.command

class LoggerCommandHandler(
    input: String
) : CommandHandler(input, "logger") {

    init {
        configPath("list") { param ->

        }
    }

    override val introduce: String
        get() = """
            Welcome to Logger Command Handler
            - list: show all *.log files as a directory
            ~
        """.trimIndent()
}