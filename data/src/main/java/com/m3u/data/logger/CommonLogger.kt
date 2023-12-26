package com.m3u.data.logger

import android.util.Log
import com.m3u.core.architecture.Publisher
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.wrapper.Message
import javax.inject.Inject

/**
 * This is a wrapper of android logcat.
 * Its tag is always be "Logger".
 * You can only output info and error level logs.
 * And it only be effective when package is debug mode.
 *
 * This is the default Logger implement.
 */
class CommonLogger @Inject constructor(
    @Publisher.App private val publisher: Publisher
) : Logger {
    override fun log(
        text: String,
        level: Int,
        tag: String
    ) {
        if (publisher.debug) {
            when (level) {
                Message.LEVEL_INFO -> Log.i(tag, text)
                Message.LEVEL_WARN -> Log.w(tag, text)
                Message.LEVEL_ERROR -> Log.e(tag, text)
            }
        }
    }

    override fun log(
        throwable: Throwable,
        tag: String
    ) {
        if (publisher.debug) {
            Log.e(tag, throwable.message, throwable)
        }
    }
}