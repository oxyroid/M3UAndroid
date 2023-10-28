package com.m3u.core.util.collections

inline fun <E> Array<E?>.forEachNotNull(block: (E) -> Unit) {
    forEach {
        it?.let(block)
    }
}

inline fun <E> Collection<E?>.forEachNotNull(block: (E) -> Unit) {
    forEach {
        it?.let(block)
    }
}
