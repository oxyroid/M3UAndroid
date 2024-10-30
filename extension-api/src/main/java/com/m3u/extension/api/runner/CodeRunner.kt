package com.m3u.extension.api.runner

abstract class CodeRunner(name: String) : Runner(name) {
    abstract fun run()
}
