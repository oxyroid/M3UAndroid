package com.m3u.extension.runtime

internal object Utils {
    fun Result<*>.asProtoResult(): com.m3u.extension.api.model.Result {
        return if (isSuccess) {
            com.m3u.extension.api.model.Result(
                success = true
            )
        } else {
            com.m3u.extension.api.model.Result(
                success = false,
                message = this.exceptionOrNull()?.message
            )
        }
    }
}