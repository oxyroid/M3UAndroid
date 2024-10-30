package com.m3u.extension.api.runner

import com.m3u.extension.api.model.EMedia
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

abstract class EMediaRunner(name: String) : Runner(name) {
    abstract fun parse(input: InputStream): Flow<EMedia>
}