package com.m3u.core.architecture.logger

import android.util.Log
import com.m3u.core.architecture.Publisher
import java.io.File
import javax.inject.Inject

class AndroidLogger @Inject constructor(
    private val publisher: Publisher
) : Logger {
    override fun log(s: String) {
        if (publisher.debug) {
            Log.i("AndroidLogger", s)
        }
    }

    override fun log(throwable: Throwable) {
        if (publisher.debug) {
            Log.e("AndroidLogger", "", throwable)
        }
    }

    override fun readAll(): List<File> {
        error("Common logger cannot read log history.")
    }
}