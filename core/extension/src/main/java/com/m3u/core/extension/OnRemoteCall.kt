package com.m3u.core.extension

import com.m3u.extension.api.IRemoteCallback

interface OnRemoteCall {
    suspend operator fun invoke(module: String, method: String, bytes: ByteArray, callback: IRemoteCallback?)
    fun setDependencies(dependencies: RemoteServiceDependencies)
    companion object {
        const val ERROR_CODE_MODULE_NOT_FOUNDED = -1
        const val ERROR_CODE_METHOD_NOT_FOUNDED = -2
        const val ERROR_CODE_UNCAUGHT = -3
    }
}

class RemoteCallException(
    val errorCode: Int,
    val errorMessage: String?
) : RuntimeException(errorMessage)
