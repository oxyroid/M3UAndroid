package com.m3u.androidApp.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.architecture.configuration.ExperimentalConfiguration
import com.m3u.core.architecture.viewmodel.BaseViewModel
import com.m3u.core.wrapper.Event
import com.m3u.core.wrapper.eventOf
import com.m3u.core.wrapper.handledEvent
import com.m3u.data.service.UiService
import com.m3u.ui.Action
import com.m3u.ui.Destination
import com.m3u.ui.Fob
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    application: Application,
    configuration: Configuration,
    uiService: UiService
) : BaseViewModel<RootState, Unit>(
    application = application,
    emptyState = RootState(
        configuration = configuration
    )
) {
    init {
        restore()
    }

    val snacker = uiService.snacker

    val title: MutableStateFlow<String> = MutableStateFlow("")
    val actions: MutableStateFlow<ImmutableList<Action>> = MutableStateFlow(persistentListOf())
    val fob: MutableStateFlow<Fob?> = MutableStateFlow(null)

    override fun onEvent(event: Unit) {

    }

    private fun restore() {
        val rootDestination = Destination.Root.entries[rootDestination()]
        writable.update {
            it.copy(
                rootDestination = eventOf(rootDestination)
            )
        }
    }

    private fun rootDestination(): Int {
        val index = readable.initialRootDestination
        val size = Destination.Root.entries.size
        // maybe version upgrade will increase root destination count
        if (index < 0 || index > size - 1) return 0
        return index
    }
}

@OptIn(ExperimentalConfiguration::class)
data class RootState(
    val rootDestination: Event<Destination.Root> = handledEvent(),
    private val configuration: Configuration
) {
    var cinemaMode: Boolean by configuration.cinemaMode
    var initialRootDestination: Int by configuration.initialRootDestination
}
