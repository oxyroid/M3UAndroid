package com.m3u.data.repository.impl

import org.junit.Test

internal class FeedRepositoryImplTest {
    @Test
    fun testFindParentPath() {
        println("https://t19.cdn2020.com/video/m3u8/2022/07/01/35e32d1c/index.m3u8".findParentPath())
    }

    private fun String.findParentPath(): String? {
        val index = lastIndexOf("/")
        if (index == -1) return null
        return take(index + 1)
    }
}