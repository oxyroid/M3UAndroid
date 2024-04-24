package com.m3u.data.logger

import android.util.Log
import com.m3u.core.architecture.Publisher
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.wrapper.Message
import javax.inject.Inject
import kotlin.time.Duration

class StubLogger @Inject constructor(
    private val publisher: Publisher
) : Logger {
    override fun log(
        text: String,
        level: Int,
        tag: String,
        duration: Duration,
        type: Int
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
        level: Int,
        tag: String
    ) {
        if (publisher.debug) {
            when (level) {
                Message.LEVEL_EMPTY -> {}
                Message.LEVEL_INFO -> {
                    Log.i(tag, throwable.message, throwable)
                }

                Message.LEVEL_WARN -> {
                    Log.w(tag, throwable.message, throwable)
                }

                Message.LEVEL_ERROR -> {
                    Log.e(tag, throwable.message, throwable)
                }

                else -> {}
            }
        }
    }
}