package com.m3u.extension.app

import com.m3u.extension.api.model.EChannel
import com.m3u.extension.api.model.EMedia
import com.m3u.extension.api.runner.EMediaRunner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.InputStream
import kotlin.time.Duration.Companion.seconds

// runner must keep public and has an empty constructor
class XXEMediaRunner : EMediaRunner("name") {
    override fun parse(input: InputStream): Flow<EMedia> = flow {
        delay(1.seconds)
        emit(
            EChannel(
                "name",
                "url",
                "category",
                "cover",
                "playlistUrl",
                "licenseType",
                "licenseKey"
            )
        )
    }
}