@file:Suppress("unused")

package com.m3u.data.remote.parser.post

import com.m3u.data.database.entity.Post
import com.m3u.data.remote.parser.post.impl.s1.PostParser1
import com.m3u.data.remote.parser.post.impl.unsupported.UnsupportedPostParser

interface PostParser<out O : Output> {
    fun parse(input: Post): O?

    companion object {
        fun parse(input: Post): Output? = when (input.standard) {
            1 -> standard1
            else -> unsupportedStandard
        }
            .parse(input)

        private val standard1: PostParser<Output> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            PostParser1()
        }

        private val unsupportedStandard: PostParser<Output> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            UnsupportedPostParser
        }
    }
}