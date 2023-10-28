package com.m3u.data.repository

import com.m3u.core.wrapper.Resource
import java.io.File
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun savePicture(url: String): Flow<Resource<File>>
    fun shareFiles(files: List<File>): Result<Unit>
}
