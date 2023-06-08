package com.m3u.core.architecture.logger

import android.util.Log
import com.m3u.core.annotation.AppPublisherImpl
import com.m3u.core.architecture.Publisher

/**
 * This is a wrapper of android logcat.
 * Its tag is always be "AndroidLogger".
 * You can only output info and error level logs.
 * And it only be effective when package is debug mode.
 *
 * This is the default Logger implement.
 */
class AndroidLogger constructor(
    @AppPublisherImpl private val publisher: Publisher
) : Logger {
    override fun log(text: String) {
        if (publisher.debug) {
            Log.i("AndroidLogger", text)
        }
    }

    override fun log(throwable: Throwable) {
        if (publisher.debug) {
            Log.e("AndroidLogger", "", throwable)
        }
    }
}