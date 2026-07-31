package com.m3u.smartphone.startup

import com.m3u.smartphone.BuildConfig
import java.net.URI

internal data class DebugEmbyAccountConfiguration(
    val title: String,
    val baseUrl: String,
    val username: String,
    val password: String,
) {
    companion object {
        fun configuredOrNull(): DebugEmbyAccountConfiguration? = fromValues(
            baseUrl = BuildConfig.DEBUG_EMBY_BASE_URL,
            username = BuildConfig.DEBUG_EMBY_USERNAME,
            password = BuildConfig.DEBUG_EMBY_PASSWORD,
        )

        internal fun fromValues(
            baseUrl: String,
            username: String,
            password: String,
        ): DebugEmbyAccountConfiguration? {
            val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
            val normalizedUsername = username.trim()
            val values = listOf(normalizedBaseUrl, normalizedUsername, password)
            if (values.all(String::isBlank)) return null
            require(values.none(String::isBlank)) {
                "The local debug Emby fixture is only partially configured"
            }
            require(normalizedBaseUrl.length <= MAXIMUM_BASE_URL_LENGTH) {
                "The local debug Emby server URL is too long"
            }
            require(normalizedUsername.length <= MAXIMUM_USERNAME_LENGTH) {
                "The local debug Emby username is too long"
            }
            require(password.length <= MAXIMUM_PASSWORD_LENGTH) {
                "The local debug Emby password is too long"
            }
            val uri = runCatching { URI(normalizedBaseUrl) }
                .getOrElse {
                    throw IllegalArgumentException(
                        "The local debug Emby server URL is invalid",
                        it,
                    )
                }
            require(
                uri.scheme.equals("https", ignoreCase = true) &&
                    !uri.host.isNullOrBlank() &&
                    uri.rawUserInfo == null &&
                    uri.rawQuery == null &&
                    uri.rawFragment == null &&
                    uri.port in -1..65_535
            ) {
                "The local debug Emby server URL must be an HTTPS base URL"
            }
            return DebugEmbyAccountConfiguration(
                title = DEFAULT_TITLE,
                baseUrl = normalizedBaseUrl,
                username = normalizedUsername,
                password = password,
            )
        }

        private const val DEFAULT_TITLE = "Debug Emby"
        private const val MAXIMUM_BASE_URL_LENGTH = 2_048
        private const val MAXIMUM_USERNAME_LENGTH = 512
        private const val MAXIMUM_PASSWORD_LENGTH = 4_096
    }
}
