package com.m3u.core.util.collections

@Suppress("UNCHECKED_CAST")
fun <K, V> Map<K?, V>.filterNotNullKeys(): Map<K, V> = this.filterKeys { it != null } as Map<K, V>
