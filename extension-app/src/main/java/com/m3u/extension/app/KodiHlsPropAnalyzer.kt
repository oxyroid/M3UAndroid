package com.m3u.extension.app

import com.m3u.extension.api.analyzer.HlsPropAnalyzer

class KodiHlsPropAnalyzer : HlsPropAnalyzer {
    override val name: String = "KODI HLS Property Analyzer"
    override val description: String = """
        This analyzer adds Kodi license support, included
        license_type and license_key. 
        """.trimIndent().replace("\n+".toRegex(), " ")

    override val priority: Int = 8

    override fun onAnalyze(
        protocol: String,
        key: String,
        value: String
    ): HlsPropAnalyzer.HlsResult = if (protocol != KODI_MARK) HlsPropAnalyzer.HlsResult.NotHandled
    else when (key) {
        KODI_LICENSE_TYPE -> HlsPropAnalyzer.HlsResult.LicenseType(key)
        KODI_LICENSE_KEY -> HlsPropAnalyzer.HlsResult.LicenseKey(key)
        else -> HlsPropAnalyzer.HlsResult.NotHandled
    }

    companion object {
        private const val KODI_MARK = "KODIPROP"
        private const val KODI_LICENSE_TYPE = "inputstream.adaptive.license_type"
        private const val KODI_LICENSE_KEY = "inputstream.adaptive.license_key"
    }
}