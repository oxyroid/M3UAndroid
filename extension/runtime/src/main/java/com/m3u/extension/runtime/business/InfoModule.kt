package com.m3u.extension.runtime.business

import com.m3u.extension.api.model.GetAppInfoResponse
import com.m3u.extension.runtime.RemoteMethod
import com.m3u.extension.runtime.RemoteModule

class InfoModule : RemoteModule {
    override val module: String = "info"

    @RemoteMethod("getAppInfo")
    fun getAppInfo(
        continuation: RemoteModule.Continuation<GetAppInfoResponse>
    ) {
        continuation.resume(
            GetAppInfoResponse(
                app_id = "com.m3u.extension.runtime",
                app_version = "InfoModule",
                app_name = "1.0.0"
            )
        )
    }
}