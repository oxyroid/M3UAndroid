package com.m3u.data.parser

interface Parser<I, R> {
    suspend fun execute(input: I, callback: (count: Int, total: Int) -> Unit = { _, _ -> }): R
}
