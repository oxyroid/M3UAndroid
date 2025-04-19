package com.m3u.core.foundation.ui

import androidx.compose.runtime.Composable
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@Composable
fun composableOf(block: @Composable () -> Unit): @Composable () -> Unit = block

@OptIn(ExperimentalContracts::class)
@Composable
fun composableOf(condition: Boolean, block: @Composable () -> Unit): (@Composable () -> Unit)? {
    contract { returnsNotNull() implies condition }
    return if (condition) {
        block
    } else {
        null
    }
}

@Composable
fun <S> composableOf(block: @Composable S.() -> Unit): @Composable S.() -> Unit = block

@OptIn(ExperimentalContracts::class)
@Composable
fun <S> composableOf(
    condition: Boolean,
    block: @Composable S.() -> Unit
): (@Composable S.() -> Unit)? {
    contract { returnsNotNull() implies condition }
    return if (condition) {
        block
    } else {
        null
    }
}
