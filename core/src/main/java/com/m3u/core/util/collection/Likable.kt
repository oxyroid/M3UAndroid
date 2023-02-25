@file:Suppress("unused")

package com.m3u.core.util.collection

interface Likable<T> {
    infix fun like(another: T): Boolean = this == another
}


infix fun <T : Likable<T>> T.notlike(another: T): Boolean = this.like(another).not()
infix fun <T : Likable<T>> T.belong(c: Collection<T>): Boolean = c.any { it like this }
infix fun <T : Likable<T>> T.notbelong(c: Collection<T>): Boolean = c.all { it notlike this }

infix fun <T : Likable<T>> Collection<T>.hold(t: T): Boolean = this.any { it like t }
infix fun <T : Likable<T>> Collection<T>.nothold(t: T): Boolean = this.all { it notlike t }
