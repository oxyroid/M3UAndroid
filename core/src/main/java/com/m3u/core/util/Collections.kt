@file:Suppress("unused")

package com.m3u.core.util

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

@Suppress("UNCHECKED_CAST")
fun <K, V> Map<K?, V>.filterNotNullKeys(): Map<K, V> = this.filterKeys { it != null } as Map<K, V>

inline fun <E> Collection<E>.replaceIf(
    predicate: (E) -> Boolean,
    transform: (E) -> E
): Collection<E> {
    val collection = this.toMutableList()
    forEachIndexed { index, element ->
        if (predicate(element)) {
            val newElement = transform(element)
            collection[index] = newElement
        }
    }
    return collection
}

inline fun <E> List<E>.replaceIf(
    predicate: (E) -> Boolean,
    transform: (E) -> E
): List<E> {
    return (this as Collection<E>).replaceIf(predicate, transform).toList()
}

class UnsuitableRangeForIterable(
    iterable: Iterable<Any?>,
    range: IntRange,
    tryUntil: Boolean = false
) : Exception(
    buildString {
        append("The range($range) is not suit for your iterable($iterable).")
        if (tryUntil) append(" Try \"util\" instead of \"..\".")
    }
)

inline fun <E> Iterator<E>.indexOf(start: Int = 0, predicate: (E) -> Boolean): Int {
    var index = 0
    while (hasNext()) {
        if (index < start) continue
        if (predicate(next())) return index
        index++
    }
    return -1
}

inline fun <E> List<E>.indexOf(start: Int = 0, predicate: (E) -> Boolean): Int {
    var index = start
    while (index < lastIndex) {
        if (predicate(get(index))) return index
        index++
    }
    return -1
}