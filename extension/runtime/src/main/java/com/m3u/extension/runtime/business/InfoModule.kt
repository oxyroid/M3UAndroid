package com.m3u.extension.runtime.business

import com.m3u.extension.api.model.GetAppInfoResponse

class InfoModule : RemoteModule {
    override val module: String = "info"

    @RemoteMethod("getAppInfo")
    fun getAppInfo(): GetAppInfoResponse {
        return GetAppInfoResponse(
            app_id = "com.m3u.extension.runtime",
            app_version = "InfoModule",
            app_name = "1.0.0"
        )
    }
}