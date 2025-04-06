package com.m3u.extension.runtime

import android.app.Application
import android.content.Context
import com.m3u.extension.api.model.Result as ProtoResult

object Utils {
    internal fun Result<*>.asProtoResult(): ProtoResult {
        return if (isSuccess) {
            ProtoResult(
                success = true
            )
        } else {
            ProtoResult(
                success = false,
                message = this.exceptionOrNull()?.message
            )
        }
    }

    private lateinit var context: Application
    fun init(applicationContext: Application) {
        this.context = applicationContext
    }

    fun getContext(): Context {
        return context
    }
}