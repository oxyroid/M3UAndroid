package com.m3u.core.collection

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