package com.m3u.data.remote.parser.post.impl.unsupported

import com.m3u.data.database.entity.Post
import com.m3u.data.remote.parser.post.PostParser

object UnsupportedPostParser : PostParser<Nothing> {
    override fun parse(input: Post): Nothing? {
        error("Unsupported post standard ${input.standard}")
    }
}