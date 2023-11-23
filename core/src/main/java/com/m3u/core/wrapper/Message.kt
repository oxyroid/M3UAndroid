package com.m3u.core.wrapper

abstract class Message(
    val resId: Int,
    vararg val formatArgs: Any
)

object EmptyMessage : Message(0)
