package com.m3u.extension.runtime

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration.Companion.seconds

class ExtensionProvider(
    context: Context,
    coroutineScope: CoroutineScope
) {
    private val applicationContext = context.applicationContext

    val extensions: StateFlow<List<Extension>> = flow {
        while (true) {
            val extensions = ExtensionLoader.loadExtensions(applicationContext)
            emit(extensions)
            delay(3.seconds)
        }
    }
        .flowOn(Dispatchers.Main)
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )
}