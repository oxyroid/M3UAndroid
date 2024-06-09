package com.m3u.data.repository.other

import com.m3u.data.api.dto.github.Release

interface OtherRepository {
    suspend fun release(): Release?
}