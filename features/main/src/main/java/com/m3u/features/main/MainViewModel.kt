package com.m3u.features.main

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.BaseViewModel
import com.m3u.core.util.collection.replaceIf
import com.m3u.data.entity.Subscription
import com.m3u.data.repository.LiveRepository
import com.m3u.data.repository.SubscriptionRepository
import com.m3u.data.repository.observeLivesBySubscriptionUrl
import com.m3u.features.main.model.SubDetail
import com.m3u.features.main.model.toDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val liveRepository: LiveRepository,
    application: Application
) : BaseViewModel<MainState, MainEvent>(
    application = application,
    emptyState = MainState()
) {
    init {
        var job: Job? = null
        observeAllSubscriptions()
            .map { it.map(Subscription::toDetail) }
            .onEach(::setAllDetails)
            .onEach { details ->
                job?.cancel()
                job = viewModelScope.launch {
                    details.forEach { detail ->
                        val url = detail.subscription.url
                        observeSize(url)
                            .onEach { count -> setCountFromExistedDetails(url, count) }
                            .launchIn(this)
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeAllSubscriptions(): Flow<List<Subscription>> =
        subscriptionRepository.observeAll()

    private fun observeSize(url: String): Flow<Int> =
        liveRepository
            .observeLivesBySubscriptionUrl(url)
            .map { it.size }

    private fun setAllDetails(details: List<SubDetail>) {
        writable.update {
            it.copy(
                details = details
            )
        }
    }

    private suspend fun setCountFromExistedDetails(url: String, count: Int) {
        withContext(Dispatchers.IO) {
            val predicate: (SubDetail) -> Boolean = { it.subscription.url == url }
            val transform: (SubDetail) -> SubDetail = { it.copy(count = count) }
            val details = readable.details.replaceIf(predicate, transform)
            setAllDetails(details)
        }
    }

    override fun onEvent(event: MainEvent) {

    }
}