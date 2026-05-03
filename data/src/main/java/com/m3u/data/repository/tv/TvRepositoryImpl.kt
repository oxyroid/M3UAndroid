package com.m3u.data.repository.tv

import android.content.Context
import android.content.res.Configuration
import android.net.nsd.NsdServiceInfo
import android.provider.Settings
import com.m3u.data.BuildConfig
import com.m3u.data.api.TvApiDelegate
import com.m3u.data.tv.Utils
import com.m3u.data.tv.http.HttpServer
import com.m3u.data.tv.model.TvInfo
import com.m3u.data.tv.nsd.NsdDeviceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration

class TvRepositoryImpl @Inject constructor(
    private val nsdDeviceManager: NsdDeviceManager,
    private val httpServer: HttpServer,
    private val tvApi: TvApiDelegate,
    @param:ApplicationContext private val context: Context
) : TvRepository() {
    private val coroutineScope = CoroutineScope(SupervisorJob())
    private val isTelevision: Boolean
        get() {
            val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
            return mode == Configuration.UI_MODE_TYPE_TELEVISION || context.packageName == "com.m3u.tv"
        }

    private val _broadcastCodeOnTv = MutableStateFlow<Int?>(null)
    override val broadcastCodeOnTv: StateFlow<Int?> = _broadcastCodeOnTv.asStateFlow()

    private val _connected = MutableStateFlow<TvInfo?>(null)
    override val connected: StateFlow<TvInfo?> = _connected.asStateFlow()

    private var broadcastOnTvJob: Job? = null
    private var connectToTvJob: Job? = null
    private val timber = Timber.tag("TvRepository")

    init {
        if (isTelevision) broadcastOnTv()
    }

    override fun broadcastOnTv() {
        val serverPort = debugServerPort() ?: Utils.findPort()
        timber.d("broadcastOnTv start, port=$serverPort")
        closeBroadcastOnTv()
        httpServer.start(serverPort)
        broadcastOnTvJob = coroutineScope.launch {
            while (true) {
                val pin = Utils.createPin()
                val host = Utils.getLocalHostAddress() ?: continue
                timber.d("broadcast pin=$pin, host=$host, port=$serverPort")
                nsdDeviceManager
                    .broadcast(
                        port = serverPort,
                        pin = pin,
                        metadata = mapOf(
                            NsdDeviceManager.META_DATA_PORT to serverPort,
                            NsdDeviceManager.META_DATA_HOST to host
                        )
                    )
                    .collect { registered ->
                        timber.d("broadcast registration changed: registered=${registered != null}")
                        _broadcastCodeOnTv.value = if (registered != null) pin else null
                    }
            }
        }
    }

    override fun closeBroadcastOnTv() {
        timber.d("closeBroadcastOnTv")
        _broadcastCodeOnTv.value = null
        broadcastOnTvJob?.cancel()
        broadcastOnTvJob = null
        httpServer.stop()
    }

    override fun connectToTv(
        broadcastCode: Int,
        timeout: Duration
    ): Flow<ConnectionToTvValue> = flow {
        timber.d("connectToTv start, pin=$broadcastCode, timeout=$timeout")
        emit(ConnectionToTvValue.Searching)
        val completed = debugDirectEndpoint()
            ?: withTimeoutOrNull(timeout) {
                timber.d("NSD search collecting")
                nsdDeviceManager
                    .search()
                    .mapNotNull { services -> services.toCompletedValue(broadcastCode) }
                    .firstOrNull()
            }

        if (completed == null) {
            timber.w("connectToTv timeout, pin=$broadcastCode")
            emit(ConnectionToTvValue.Timeout)
            return@flow
        }

        timber.d("NSD matched TV: host=${completed.host}, port=${completed.port}")
        emit(ConnectionToTvValue.Connecting)
        connectToTvJob?.cancel()
        timber.d("prepare TvApi start")
        val info = tvApi.prepare(completed.host, completed.port).firstOrNull()
        if (info == null) {
            timber.w("prepare TvApi returned null")
            _connected.value = null
            emit(ConnectionToTvValue.Idle("TV did not respond."))
            return@flow
        }
        timber.d("prepare TvApi completed: $info")
        _connected.value = info
        emit(completed)
    }.flowOn(Dispatchers.IO)
        .catch { error ->
            timber.e(error, "connectToTv failed")
            _connected.value = null
            emit(ConnectionToTvValue.Idle(error.message))
        }

    override suspend fun disconnectToTv() {
        timber.d("disconnectToTv")
        connectToTvJob?.cancel()
        connectToTvJob = null
        _connected.value = null
        tvApi.close()
    }

    private fun List<NsdServiceInfo>.toCompletedValue(broadcastCode: Int): ConnectionToTvValue.Completed? {
        val info = firstOrNull {
            it.getAttribute(NsdDeviceManager.META_DATA_PIN) == broadcastCode.toString()
        } ?: return null
        val port = info.getAttribute(NsdDeviceManager.META_DATA_PORT)?.toIntOrNull() ?: return null
        val host = info.getAttribute(NsdDeviceManager.META_DATA_HOST) ?: return null
        return ConnectionToTvValue.Completed(host, port)
    }

    private fun NsdServiceInfo.getAttribute(key: String): String? = attributes[key]?.decodeToString()

    private fun debugDirectEndpoint(): ConnectionToTvValue.Completed? {
        if (!BuildConfig.DEBUG) return null
        val host = debugSetting(DEBUG_TV_HOST)?.takeIf { it.isNotBlank() } ?: return null
        val port = debugSetting(DEBUG_TV_PORT)?.toIntOrNull() ?: return null
        timber.d("debug direct TV endpoint enabled: host=$host, port=$port")
        return ConnectionToTvValue.Completed(host, port)
    }

    private fun debugServerPort(): Int? {
        if (!BuildConfig.DEBUG) return null
        return debugSetting(DEBUG_SERVER_PORT)?.toIntOrNull()
            ?.also { timber.d("debug TV server port enabled: port=$it") }
    }

    private fun debugSetting(key: String): String? = Settings.Global.getString(context.contentResolver, key)

    private companion object {
        const val DEBUG_TV_HOST = "m3u_remote_control_tv_host"
        const val DEBUG_TV_PORT = "m3u_remote_control_tv_port"
        const val DEBUG_SERVER_PORT = "m3u_remote_control_server_port"
    }
}
