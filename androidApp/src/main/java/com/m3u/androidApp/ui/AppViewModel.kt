package com.m3u.androidApp.ui

import androidx.lifecycle.ViewModel
import com.m3u.data.service.MessageService
import com.m3u.ui.Action
import com.m3u.ui.Fob
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    messageService: MessageService
) : ViewModel() {
    val message = messageService.message

    val title: MutableStateFlow<String> = MutableStateFlow("")
    val actions: MutableStateFlow<ImmutableList<Action>> = MutableStateFlow(persistentListOf())
    val fob: MutableStateFlow<Fob?> = MutableStateFlow(null)
    val deep: MutableStateFlow<Int> = MutableStateFlow(0)
}

