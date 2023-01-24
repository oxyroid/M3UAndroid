package com.m3u.core.util

import org.junit.Assert.*
import org.junit.Test

class StringsKtTest {
    @Test
    fun `trimBrackets`() {
        println("\"蓝精灵直播\"".trimBrackets())
        println("\"直播发布页地址\"".trimBrackets())
    }
}