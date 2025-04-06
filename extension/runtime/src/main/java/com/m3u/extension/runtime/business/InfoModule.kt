package com.m3u.extension.runtime.business

import com.m3u.extension.api.Method
import com.m3u.extension.api.Module
import com.m3u.extension.api.business.InfoApi
import com.m3u.extension.api.model.GetAppInfoResponse
import com.m3u.extension.api.model.GetMethodsRequest
import com.m3u.extension.api.model.GetMethodsResponse
import com.m3u.extension.api.model.GetModulesResponse

@Module("info")
class InfoModule(
    private val modules: () -> List<String>,
    private val methods: (module: String) -> List<String>
) : RemoteModule(), InfoApi {
    @Method("getAppInfo")
    override suspend fun getAppInfo(): GetAppInfoResponse {
        return GetAppInfoResponse(
            app_id = "com.m3u.smartphone",
            app_version = "InfoModule",
            app_name = "M3U",
            app_description = "Powerful Media Player",
            app_package_name = "com.m3u.smartphone"
        )
    }

    @Method("getModules")
    override suspend fun getModules(): GetModulesResponse {
        return GetModulesResponse(
            modules = modules()
        )
    }

    @Method("getMethods")
    override suspend fun getMethods(req: GetMethodsRequest): GetMethodsResponse {
        return GetMethodsResponse(
            methods = methods(req.module)
        )
    }
}