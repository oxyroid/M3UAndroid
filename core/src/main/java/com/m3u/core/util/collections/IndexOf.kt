package com.m3u.core.util.collections

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
