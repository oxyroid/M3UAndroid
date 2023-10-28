package com.m3u.core.util.transform

object IntIterativeTransferable : IterativeTransferable<Int>() {
    override fun transferElement(element: Int): String = element.toString()
    override fun acceptElement(s: String): Int = s.toInt()
    override fun affirmedElement(c: Char): Boolean = c.isDigit()
}
