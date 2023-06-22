package com.m3u.features.favorite

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.viewmodel.BaseViewModel
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.util.collection.filterNotNullKeys
import com.m3u.data.repository.FeedRepository
import com.m3u.data.repository.LiveRepository
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

class FavouriteViewModel(
    liveRepository: LiveRepository,
    feedRepository: FeedRepository,
    application: Application,
    configuration: Configuration,
) : BaseViewModel<FavoriteState, FavoriteEvent>(
    application = application,
    emptyState = FavoriteState(
        configuration = configuration
    )
) {
    init {
        liveRepository
            .observeAll()
            .map { lives ->
                lives.filter { it.favourite }
            }
            .onEach { lives ->
                writable.update { state ->
                    state.copy(
                        details = lives
                            .groupBy { it.feedUrl }
                            .mapKeys { feedRepository.get(it.key)?.title }
                            .filterNotNullKeys()
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onEvent(event: FavoriteEvent) {

    }
}
