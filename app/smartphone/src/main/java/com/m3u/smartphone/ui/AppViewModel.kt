package com.m3u.smartphone.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.worker.SubscriptionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val workManager: WorkManager,
) : ViewModel() {
    init {
        refreshProgrammes()
    }

    private fun refreshProgrammes() {
        viewModelScope.launch {
            val playlists = playlistRepository.getAllAutoRefresh()
            playlists.forEach { playlist ->
                SubscriptionWorker.epg(
                    workManager = workManager,
                    playlistUrl = playlist.url,
                    ignoreCache = true
                )
            }
        }
    }

    var code by mutableStateOf("")
    var isConnectSheetVisible by mutableStateOf(false)
}
