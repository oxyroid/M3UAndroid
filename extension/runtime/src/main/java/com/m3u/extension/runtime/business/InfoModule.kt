package com.m3u.extension.runtime.business

import com.m3u.extension.api.Method
import com.m3u.extension.api.Module
import com.m3u.extension.api.business.InfoApi
import com.m3u.extension.api.model.GetAppInfoResponse
import com.m3u.extension.api.model.GetAvailableModulesResponse

@Module("info")
class InfoModule : RemoteModule, InfoApi {
    @Method("getAppInfo")
    override suspend fun getAppInfo(): GetAppInfoResponse {
        return GetAppInfoResponse(
            app_id = "com.m3u.extension.runtime",
            app_version = "InfoModule",
            app_name = "1.0.0"
        )
    }

    @Method("getAvailableModules")
    override suspend fun getAvailableModules(): GetAvailableModulesResponse {
        return GetAvailableModulesResponse(
            module = listOf(
                "info",
                "subscribe"
            )
        )
    }
}