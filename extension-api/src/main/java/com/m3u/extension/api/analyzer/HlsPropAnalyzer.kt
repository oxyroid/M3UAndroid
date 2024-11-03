package com.m3u.extension.api.analyzer

interface HlsPropAnalyzer : Analyzer {
    /**
     * Analyzes a HLS property,
     * and it must be synchronous and non-blocking.
     *
     * #KODIPROP:user-agent=xxx
     * @param protocol will be `KODIPROP`
     * @param key will be `user-agent`
     * @param value will be `xxx`
     */
    fun onAnalyze(protocol: String, key: String, value: String): HlsResult = HlsResult.NotHandled

    /**
     * Analyzes a HLS property,
     * and it must be synchronous and non-blocking.
     *
     * #KODIPROP:{"user-agent"="xxx"}
     * @param protocol will be `KODIPROP`
     * @param json will be `{"user-agent"="xxx"}`
     */
    fun onAnalyze(protocol: String, json: String): HlsResult = HlsResult.NotHandled

    sealed interface HlsResult {
        data object NotHandled : HlsResult
        data class UserAgent(val value: String) : HlsResult
        data class LicenseType(val value: String) : HlsResult
        data class LicenseKey(val value: String) : HlsResult
    }

    companion object {
        // The maximum count of HlsPropAnalyzer allowed per extension.
        const val MAX_COUNT_HLS_PROP_ANALYZER = 1
        // The maximum count of HlsPropAnalyzer that can be loaded at runtime.
        const val TOTAL_MAX_COUNT_HLS_PROP_ANALYZER = 4
    }
}