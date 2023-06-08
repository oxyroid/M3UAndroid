package com.m3u.core.architecture.logger

import com.m3u.core.architecture.service.BannerService

@Retention(AnnotationRetention.BINARY)
annotation class BannerLoggerImpl

/**
 * A collector of banner service.
 * Its messages will be deliver to users just like a global snack bar.
 * @see BannerService
 */
class BannerLogger(
    private val bannerService: BannerService
) : Logger {
    override fun log(text: String) {
        bannerService.append(text)
    }

    override fun log(throwable: Throwable) {
        throwable.message?.let(::log)
    }
}