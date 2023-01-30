package com.m3u.features.setting

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.m3u.core.BaseViewModel
import com.m3u.core.BuildConfigProvider
import com.m3u.core.util.createClazzKey
import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.eventOf
import com.m3u.data.Configuration
import com.m3u.data.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import java.net.MalformedURLException
import java.net.URL
import javax.inject.Inject
import kotlin.properties.Delegates

@HiltViewModel
class SettingViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    buildConfigProvider: BuildConfigProvider,
    savedStateHandle: SavedStateHandle,
    application: Application,
    private val configuration: Configuration
) : BaseViewModel<SettingState, SettingEvent>(
    application = application,
    emptyState = SettingState(),
    savedStateHandle = savedStateHandle,
    key = createClazzKey<SettingViewModel>()
) {
    init {
        configuration.syncMode
        writable.update {
            it.copy(
                version = buildConfigProvider.version()
            )
        }
    }

    private var syncMode: Int by Delegates.observable(configuration.syncMode) { _, _, new ->
        configuration.syncMode = new
        writable.update {
            it.copy(
                syncMode = new
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

            is SettingEvent.OnSyncMode -> {
                syncMode = event.syncMode
            }

            SettingEvent.SubscribeUrl -> {
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
                val urlString = readable.url
                val url = try {
                    URL(urlString)
                } catch (e: MalformedURLException) {
                    writable.update {
                        val message = context.getString(R.string.failed_malformed_url, urlString)
                        it.copy(
                            adding = false,
                            message = eventOf(message)
                        )
                    }
                    return
                }
                subscriptionRepository.subscribe(title, url)
                    .onEach { resource ->
                        writable.update {
                            when (resource) {
                                Resource.Loading -> {
                                    it.copy(
                                        adding = true
                                    )
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
}