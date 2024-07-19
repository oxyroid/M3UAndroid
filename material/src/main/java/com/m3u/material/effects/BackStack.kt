package com.m3u.material.effects

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.collectLatest

@Immutable
data class BackStackEntry(
    val navigationIcon: ImageVector
)

private var BACK_STACK_ENTRY_COLLECTION by mutableStateOf<List<BackStackEntry>>(emptyList())

@Composable
fun currentBackStackEntry(): State<BackStackEntry?> = produceState<BackStackEntry?>(
    initialValue = null
) {
    snapshotFlow { BACK_STACK_ENTRY_COLLECTION }.collectLatest {
        value = it.lastOrNull()
    }
}

@Composable
fun BackStackHandler(
    enabled: Boolean = true,
    entry: BackStackEntry,
    onBack: () -> Unit
) {
    if (enabled) {
        DisposableEffect(entry) {
            BACK_STACK_ENTRY_COLLECTION += entry
            onDispose {
                BACK_STACK_ENTRY_COLLECTION -= entry
            }
        }
    }
    BackHandler(
        enabled = enabled,
        onBack = onBack
    )
}