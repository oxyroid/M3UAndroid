package com.m3u.data.repository.impl

import com.m3u.core.architecture.Logger
import com.m3u.core.wrapper.Stored
import com.m3u.data.api.DropboxApi
import com.m3u.data.repository.CloudRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.io.InputStream
import javax.inject.Inject

internal data class DropboxCloud(
    val input: InputStream,
    val id: Int
) : Stored<DropboxCloud, File> {
    override fun File.restore(): DropboxCloud = DropboxCloud(
        input = inputStream(),
        id = id
    )

    override fun store(): File {
        TODO()
    }
}


internal class DropboxCloudRepository @Inject constructor(
    private val api: DropboxApi,
    private val logger: Logger,
    private val socket: Any,
    coroutineScope: CoroutineScope
) : CloudRepository<DropboxCloud, File> {
    private val source = MutableSharedFlow<List<DropboxCloud>>()

    private val state = source.stateIn(
        scope = coroutineScope,
        started = SharingStarted.Lazily,
        initialValue = emptyList()
    )

    override fun observe(id: Int): Flow<DropboxCloud?> = channelFlow {
        source
            .mapNotNull { all -> all.find { it.id == id } }
            .onEach(::send)
            .collect()
    }
        .flowOn(Dispatchers.IO)

    override fun observeAll(): Flow<List<DropboxCloud>> = source.asSharedFlow()

    override suspend fun get(id: Int): DropboxCloud? = state.value.find { it.id == id }

    override suspend fun save(e: DropboxCloud) {

        source.emit(state.value + e)
    }

    override suspend fun delete(e: DropboxCloud) {
        TODO("Not yet implemented")
    }

    override suspend fun deleteAll() {
        TODO("Not yet implemented")
    }
}
