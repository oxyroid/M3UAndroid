package com.m3u.core.util.transform

abstract class IterativeTransferable<T> : Transferable<Iterable<T>> {
    abstract fun affirmedElement(c: Char): Boolean
    abstract fun transferElement(element: T): String
    final override fun transfer(src: Iterable<T>): String {
        return src.joinToString(
            prefix = "[",
            postfix = "]",
            separator = ",",
            transform = ::transferElement
        )
    }

    abstract fun acceptElement(s: String): T
    final override fun accept(dest: String): Iterable<T> = buildList {
        var current = ""
        dest.trim().forEachIndexed { index, c ->
            if (index == 0 && c != '[') return emptyList()
            if (index == dest.lastIndex && c != ']') return emptyList()
            if (c == ',') {
                if (current.isNotEmpty()) add(acceptElement(current))
                current = ""
            } else if (affirmedElement(c)) {
                current += c
            }
        }
        if (current.isNotEmpty()) {
            add(acceptElement(current))
        }
    }
}