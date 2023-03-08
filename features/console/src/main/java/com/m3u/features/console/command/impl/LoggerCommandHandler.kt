package com.m3u.features.console.command.impl

import com.m3u.features.console.command.CommandHandler
import java.io.File

internal class LoggerCommandHandler(
    readAllLogFiles: () -> List<File>,
    clearAllLogFiles: () -> Unit,
    shareLogFiles: (List<File>) -> Unit,
    input: String
) : CommandHandler(input) {

    init {
        path("list") {
            val text = if (param.isNullOrEmpty()) {
                readAllLogFiles().joinToString(
                    separator = "\n",
                    postfix = "\n",
                    transform = { it.path }
                )
            } else {
                val file = when (param) {
                    "-i" -> readAllLogFiles().lastOrNull()
                    else -> readAllLogFiles().find { it.path == arg }
                }
                file?.readText() ?: ""
            }
            output(text)
        }

        path("share") {
            val files = if (param.isNullOrEmpty()) readAllLogFiles()
            else listOfNotNull(readAllLogFiles().lastOrNull())
            shareLogFiles(files)
        }

        path("clear") {
            clearAllLogFiles()
        }
    }

    override val introduce: String
        get() = """
            Welcome to Logger Command Handler
            - list: show all *.log files as a directory.
            - list -l: show content of latest log file.
            - list log.txt: show content of target log file.
            - share: share all log files.
            - share -l: share latest log file.
            - clear: delete all log files.
            ~
        """.trimIndent()

    companion object {
        const val KEY = "logger"
    }
}