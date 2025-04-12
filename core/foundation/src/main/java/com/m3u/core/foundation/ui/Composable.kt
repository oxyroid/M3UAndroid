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
fun <T> composableOf(block: @Composable (T) -> Unit): @Composable (T) -> Unit = block
