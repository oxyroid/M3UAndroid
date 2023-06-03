@file:Suppress("unused")
package com.m3u.data.service

import com.eclipsesource.v8.V8

object JavaScriptExecutor {
    fun execute(script: String) {
        val runtime = V8.createV8Runtime()
        runtime.executeVoidScript(script)
        runtime.release(true)
    }

    fun executeString(script: String): String {
        val runtime = V8.createV8Runtime()
        val result = runtime.executeStringScript(script)
        runtime.release(true)
        return result
    }

    fun executeInt(script: String): Int {
        val runtime = V8.createV8Runtime()
        val result = runtime.executeIntegerScript(script)
        runtime.release(true)
        return result
    }

    fun executeBoolean(script: String): Boolean {
        val runtime = V8.createV8Runtime()
        val result = runtime.executeBooleanScript(script)
        runtime.release(true)
        return result
    }
}
