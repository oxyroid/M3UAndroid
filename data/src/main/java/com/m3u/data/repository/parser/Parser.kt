package com.m3u.data.repository.parser

interface Parser<I, R> {
    suspend fun execute(input: I): R
}
