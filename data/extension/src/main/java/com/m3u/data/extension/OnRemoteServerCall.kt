package com.m3u.data.extension

interface OnRemoteServerCall {
    fun onCall(module: String, method: String, param: String, callback: IRemoteCallback?)
    companion object {
        const val ERROR_CODE_MODULE_NOT_FOUNDED = -1
        const val ERROR_CODE_METHOD_NOT_FOUNDED = -2
        const val ERROR_CODE_UNCAUGHT = -3
    }
}

class RemoteCallException(
    val errorCode: Int,
    val errorMessage: String?
): RuntimeException(errorMessage)
