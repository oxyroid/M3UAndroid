package com.m3u.data.logger

import com.m3u.core.architecture.Logger
import com.m3u.data.service.Message
import com.m3u.data.service.UiService
import java.util.Locale
import javax.inject.Inject

/**
 * A collector of banner service.
 * Its messages will be destreamr to users just like a global snack bar.
 * @see UiService
 */
class UiLogger @Inject constructor(
    private val uiService: UiService,
    private val logger: Logger
) : Logger {
    override fun log(text: String) {
        val value = text
            .replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.ROOT)
                else it.toString()
            }
        uiService.snack(Message(value))
    }

    override fun log(throwable: Throwable) {
        val info = throwable.stackTraceToString()
        throwable.message?.let(::log)
        logger.log(
            """
            ${throwable.message}
            $info
            =====
            """.trimIndent()
        )
    }
}
