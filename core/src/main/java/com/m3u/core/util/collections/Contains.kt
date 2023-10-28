package com.m3u.core.util.collections

inline fun <E> Iterable<E>.contains(predicate: (E) -> Boolean): Boolean = find(predicate) != null
