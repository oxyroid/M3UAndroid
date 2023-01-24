package com.m3u.core.collection

import org.junit.Assert
import org.junit.Test

class LoopInKtTest {
    @Test
    fun `loopIn`() {
        var i = 0
        val list = List(20) { it }
        list.loopIn { i++ }
        Assert.assertEquals(list.size, i)

        i = 0
        // When size = 3, means 1 until 3, in other word: 1,2.
        // the block repeat 2 times, index is 1, 2.
        // now size - 1 == 2
        // equals!
        list.loopIn(1 until list.size) { i++ }
        // success!
        Assert.assertEquals(list.size - 1, i)

        i = 0
        // When size = 3, means 1..3, in other word: 1,2,3.
        // the block repeat 3 times, index = 1, 2, 3.
        // now size - 1 = 2
        // list.loopIn(1..list.size) { i++ }
        // success? why??
        // Assert.assertEquals(list.size - 1, i)

    }
}