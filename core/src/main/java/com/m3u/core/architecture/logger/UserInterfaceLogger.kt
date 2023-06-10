package com.m3u.core.architecture.logger

import com.m3u.core.architecture.service.UserInterface

class UserInterfaceLogger(
    private val userInterface: UserInterface
) : Logger {
    override fun log(text: String) {
        userInterface.append(text)
    }

    override fun log(throwable: Throwable) {
        throwable.message?.let(::log)
    }
}