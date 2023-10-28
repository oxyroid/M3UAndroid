package com.m3u.data.logger

import android.util.Log
import com.m3u.core.architecture.Publisher
import com.m3u.core.architecture.Logger
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
    override fun log(text: String) {
        if (publisher.debug) {
            Log.i("Logger", text)
        }
    }

    override fun log(throwable: Throwable) {
        if (publisher.debug) {
            Log.e("Logger", "", throwable)
        }
    }
}
