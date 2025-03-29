package com.m3u.core.foundation.ui

import androidx.compose.runtime.Composable

@Composable
fun composableOf(block: @Composable () -> Unit): @Composable () -> Unit = block

@Composable
fun composableOf(condition: Boolean, block: @Composable () -> Unit): (@Composable () -> Unit)? = block.takeIf { condition }

