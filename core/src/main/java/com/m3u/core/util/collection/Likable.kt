@file:Suppress("unused")

package com.m3u.core.util.collection

interface Likable<T> {
    infix fun like(another: T): Boolean = this == another
}

infix fun <T : Likable<T>> T.notlike(another: T): Boolean = this.like(another).not()
infix fun <T : Likable<T>> T.belong(collection: Collection<T>): Boolean =
    collection.any { it like this }

infix fun <T : Likable<T>> T.notbelong(collection: Collection<T>): Boolean =
    collection.all { it notlike this }

infix fun <T : Likable<T>> Collection<T>.hold(element: T): Boolean = this.any { it like element }
infix fun <T : Likable<T>> Collection<T>.nothold(element: T): Boolean =
    this.all { it notlike element }
