package com.m3u.data.remote.parser.post.impl.s1

import com.m3u.data.database.entity.Post
import com.m3u.data.remote.parser.post.PostParser

class PostParser1 : PostParser<Output1> {
    override fun parse(input: Post): Output1 {
        val text = input.content.lines()
        return Output1(
            text = text
        )
    }
}