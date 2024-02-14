package com.m3u.androidApp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.m3u.core.architecture.pref.Pref
import com.m3u.data.service.MessageManager
import com.m3u.ui.Destination
import com.m3u.ui.helper.Action
import com.m3u.ui.helper.Fob
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    messageManager: MessageManager,
    pref: Pref
) : ViewModel() {
    val message = messageManager.message

    var rootDestination: Destination.Root by mutableStateOf(
        when(pref.rootDestination) {
            0 -> Destination.Root.Foryou
            1 -> Destination.Root.Favourite
            2 -> Destination.Root.Setting
            else -> Destination.Root.Foryou
        }
    )
    val title: MutableStateFlow<String> = MutableStateFlow("")
    val actions: MutableStateFlow<ImmutableList<Action>> = MutableStateFlow(persistentListOf())
    val fob: MutableStateFlow<Fob?> = MutableStateFlow(null)
    val deep: MutableStateFlow<Int> = MutableStateFlow(0)
}

