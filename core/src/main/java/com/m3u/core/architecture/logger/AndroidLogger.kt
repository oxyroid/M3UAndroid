package com.m3u.core.architecture.logger

import android.util.Log
import com.m3u.core.annotation.AppPublisherImpl
import com.m3u.core.architecture.Publisher
import javax.inject.Inject

class AndroidLogger @Inject constructor(
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