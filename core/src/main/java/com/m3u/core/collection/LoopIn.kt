@file:Suppress("unused")

package com.m3u.core.collection

/**
 * WARNING! This [loopIn] version may throw an error after block looping
 * if the range is not suitable for your iterable.
 *
 * So make sure providing a suitable range.
 * @see Collection.loopIn
 */
inline fun <E> Iterable<E>.loopIn(range: IntRange, block: (E) -> Unit) {
    if (range.first < 0 || range.first > range.last) throw UnsuitableRangeForIterable(this, range)
    var i = 0
    forEachIndexed { index, e ->
        if (range.contains(index)) {
            block(e)
            i++
        }
    }

    if (i != range.last - range.first + 1) {
        throw UnsuitableRangeForIterable(this, range, true)
    }
}

inline fun <E> Collection<E>.loopIn(range: IntRange = indices, block: (E) -> Unit) {
    if (range.first < 0 || range.first > range.last) {
        throw UnsuitableRangeForIterable(this, range)
    }
    if (range.last > size - 1) {
        throw UnsuitableRangeForIterable(this, range, true)
    }
    forEachIndexed { index, e ->
        if (range.contains(index)) {
            block(e)
        }
    }
}
