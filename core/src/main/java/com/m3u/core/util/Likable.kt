@file:Suppress("unused")

package com.m3u.core.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

// TODO: use ksp to generate code.
interface Likable<T> {
    infix fun like(another: T): Boolean = this == another
}

infix fun <T : Likable<T>> T.unlike(another: T): Boolean = this.like(another).not()
infix fun <T : Likable<T>> T.belong(collection: Collection<T>): Boolean =
    collection.any { it like this }

infix fun <T : Likable<T>> T.notbelong(collection: Collection<T>): Boolean =
    collection.all { it unlike this }

infix fun <T : Likable<T>> Collection<T>.hold(element: T): Boolean = this.any { it like element }
infix fun <T : Likable<T>> Collection<T>.nothold(element: T): Boolean =
    this.all { it unlike element }

fun <T: Likable<T>> Flow<T>.distinctUntilUnlike(): Flow<T> =
    distinctUntilChanged { old, new -> old like new }

class A: Likable<A>
fun main() {
    flowOf(A()).distinctUntilUnlike()
}

