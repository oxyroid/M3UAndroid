package com.m3u.core.util

import org.junit.Test
import java.util.*

class PropertiesKtTest {
    @Test
    fun `Parse line to properties`() {
        val properties = Properties()
        properties.loadLine("age=2")
        try {
            properties.loadLine("age=")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        properties.loadLine("=2")
        println(properties)
    }
}