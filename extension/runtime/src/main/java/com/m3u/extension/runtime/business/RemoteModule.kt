package com.m3u.extension.runtime.business

import com.m3u.extension.api.model.Result
import com.m3u.extension.runtime.Utils.asProtoResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

abstract class RemoteModule(
    val dispatcher: CoroutineDispatcher = Dispatchers.Default
)

internal suspend inline fun RemoteModule.result(
    crossinline block: suspend () -> Unit
): Result = withContext(dispatcher) {
    runCatching { block() }.asProtoResult()
}