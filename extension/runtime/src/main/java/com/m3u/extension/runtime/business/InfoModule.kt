package com.m3u.extension.runtime.business

import GetAppInfoRequest
import GetAppInfoResponse
import com.m3u.extension.runtime.RemoteMethod
import com.m3u.extension.runtime.RemoteMethodParam
import com.m3u.extension.runtime.RemoteModule

class InfoModule constructor(): RemoteModule {
    override val module: String = "info"

    @RemoteMethod("getAppInfo")
    fun getAppInfo(
        @RemoteMethodParam param: GetAppInfoRequest,
        callback: (GetAppInfoResponse) -> Unit
    ) {
        callback(
            GetAppInfoResponse(
                "com.m3u.extension.runtime",
                "InfoModule",
                "1.0.0"
            )
        )
    }
}