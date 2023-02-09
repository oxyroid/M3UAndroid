package com.m3u.features.setting

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.BaseViewModel
import com.m3u.core.architecture.PackageProvider
import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.eventOf
import com.m3u.data.Configuration
import com.m3u.data.repository.FeedRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import kotlin.properties.Delegates

@HiltViewModel
class SettingViewModel @Inject constructor(
    private val feedRepository: FeedRepository,
    packageProvider: PackageProvider,
    application: Application,
    configuration: Configuration
) : BaseViewModel<SettingState, SettingEvent>(
    application = application,
    emptyState = SettingState()
) {
    init {
        writable.update {
            it.copy(
                version = packageProvider.getVersionName(),
                syncMode = configuration.syncMode,
                useCommonUIMode = configuration.useCommonUIMode
            )
        }
    }

    private var syncMode: Int by sharedDelegate(configuration.syncMode) { newValue ->
        configuration.syncMode = newValue
        writable.update {
            it.copy(
                syncMode = newValue
            )
        }
    }
    private var useCommonUIMode: Boolean by sharedDelegate(configuration.useCommonUIMode) { newValue ->
        configuration.useCommonUIMode = newValue
        writable.update {
            it.copy(
                useCommonUIMode = newValue
            )
        }
    }


    override fun onEvent(event: SettingEvent) {
        when (event) {
            is SettingEvent.OnTitle -> {
                writable.update {
                    it.copy(
                        title = event.title
                    )
                }
            }

            is SettingEvent.OnUrl -> {
                writable.update {
                    it.copy(
                        url = event.url
                    )
                }
            }

            is SettingEvent.OnSyncMode -> syncMode = event.syncMode

            SettingEvent.OnUIMode -> useCommonUIMode = !useCommonUIMode

            SettingEvent.OnSubscribe -> {
                val title = writable.value.title
                if (title.isEmpty()) {
                    writable.update {
                        val message = context.getString(R.string.failed_empty_title)
                        it.copy(
                            adding = false,
                            message = eventOf(message)
                        )
                    }
                    return
                }
                val url = readable.url
                feedRepository.subscribe(title, url)
                    .onEach { resource ->
                        writable.update {
                            when (resource) {
                                Resource.Loading -> {
                                    it.copy(adding = true)
                                }

                                is Resource.Success -> {
                                    val message = context.getString(R.string.success_subscribe)
                                    it.copy(
                                        adding = false,
                                        title = "",
                                        url = "",
                                        message = eventOf(message)
                                    )
                                }

                                is Resource.Failure -> {
                                    it.copy(
                                        adding = false,
                                        message = eventOf(resource.message.orEmpty())
                                    )
                                }
                            }
                        }
                    }
                    .launchIn(viewModelScope)
            }
        }
    }

    private inline fun <T> sharedDelegate(observer: T, crossinline updated: (T) -> Unit) =
        Delegates.observable(observer) { _, _, newValue ->
            updated(newValue)
        }
}