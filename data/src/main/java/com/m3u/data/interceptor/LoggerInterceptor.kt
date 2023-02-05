package com.m3u.data.interceptor

import android.util.Log
import com.m3u.data.BuildConfig

class LoggerInterceptor<T : Any> : Interceptor<T> {
    override fun onPreHandle(line: String) {
        if (BuildConfig.DEBUG) {
            Log.d("LogInterceptor", "onPreHandler: $line")
        }
        super.onPreHandle(line)
    }
}