@file:Suppress("unused")
package com.m3u.core.util.collections

inline fun <E> Array<E>.withEach(block: E.() -> Unit) {
    forEach(block)
}

inline fun <E> Collection<E>.withEach(block: E.() -> Unit) {
    forEach(block)
}

inline fun <E> Array<E?>.withEachNotNull(block: E.() -> Unit) {
    forEachNotNull(block)
}

inline fun <E> Collection<E?>.withEachNotNull(block: E.() -> Unit) {
    forEachNotNull(block)
}
