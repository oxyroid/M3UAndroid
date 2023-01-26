package com.m3u.core.util

inline fun <reified C : Any> createClazzKey(): String {
    return C::class.java.name
}