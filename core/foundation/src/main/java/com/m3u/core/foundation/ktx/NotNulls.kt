package com.m3u.core.foundation.ktx

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
inline fun <T> onlyNonNull(t: T?, block: (T) -> Unit) {
    contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
    if (t != null) {
        block(t)
    }
}
@OptIn(ExperimentalContracts::class)
inline fun <T1, T2> onlyNonNull(t1: T1?, t2: T2?, block: (T1, T2) -> Unit) {
    contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
    if (t1 != null && t2 != null) {
        block(t1, t2)
    }
}