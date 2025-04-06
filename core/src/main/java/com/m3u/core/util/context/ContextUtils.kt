package com.m3u.core.util.context

import android.app.Application
import android.content.Context

object ContextUtils {
    private lateinit var context: Application
    fun init(applicationContext: Application) {
        this.context = applicationContext
    }
    fun getContext(): Context {
        return context
    }
}