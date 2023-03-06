package com.m3u.data.repository

import com.m3u.core.wrapper.Resource
import kotlinx.coroutines.flow.Flow
import java.io.File

interface MediaRepository {
    fun savePicture(url: String): Flow<Resource<File>>
    fun readAllLogFiles(): List<File>
    fun clearAllLogFiles()
}