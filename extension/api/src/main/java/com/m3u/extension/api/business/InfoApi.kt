package com.m3u.extension.api.business

import com.m3u.extension.api.Method
import com.m3u.extension.api.Module
import com.m3u.extension.api.model.GetAppInfoResponse
import com.m3u.extension.api.model.GetAvailableModulesResponse

@Module("info")
interface InfoApi {
    @Method("getAppInfo")
    suspend fun getAppInfo(): GetAppInfoResponse

    @Method("getAvailableModules")
    suspend fun getAvailableModules(): GetAvailableModulesResponse
}