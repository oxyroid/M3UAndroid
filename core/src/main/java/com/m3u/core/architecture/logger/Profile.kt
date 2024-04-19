package com.m3u.core.architecture.logger

import com.m3u.core.wrapper.Message

class Profile(
    val name: String,
    val level: Int = Message.LEVEL_ERROR
)
