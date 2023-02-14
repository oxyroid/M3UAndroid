@file:Suppress("unused")

package com.m3u.core.util.collection

interface Likable<T> {
    infix fun like(another: T): Boolean = this == another
}

infix fun <T : Likable<T>> T.belong(c: Collection<T>): Boolean = c.any { it like this }

infix fun <T : Likable<T>> Collection<T>.hold(t: T): Boolean = this.any { it like t }
